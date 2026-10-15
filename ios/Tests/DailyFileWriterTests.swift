//
//  DailyFileWriterTests.swift
//
//  Standalone tests for DailyFileWriter. They need only Foundation, so they run
//  on a Mac without Xcode project or Flutter:
//
//    cd ios
//    swiftc -parse-as-library Classes/DailyFileWriter.swift Tests/DailyFileWriterTests.swift -o /tmp/dfw_tests
//    /tmp/dfw_tests
//    # And again under a non-Gregorian calendar, which is the case the writer guards against:
//    /tmp/dfw_tests -AppleLocale 'th_TH@calendar=buddhist' -AppleLanguages '(th)'
//
//  This folder is outside Classes/, so CocoaPods does not compile it into the app.
//

import Foundation

@main
struct DailyFileWriterTests {
    static var failures = 0
    static var checks = 0

    static func check(_ ok: Bool, _ message: String) {
        checks += 1
        if !ok {
            failures += 1
            print("FAIL: \(message)")
        }
    }

    static func tempDir() -> URL {
        let url = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("dfw_\(UUID().uuidString)")
        try! FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static func read(_ url: URL) -> String? { try? String(contentsOf: url, encoding: .utf8) }

    /// 2026-10-15 12:00:00 in [zone].
    static func date(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ mi: Int, _ s: Int, in zone: TimeZone) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        return cal.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: mi, second: s))!
    }

    static func main() {
        let utc = TimeZone(identifier: "UTC")!
        let kolkata = TimeZone(identifier: "Asia/Kolkata")!

        // 1. The file name is the day, in the Gregorian calendar.
        do {
            let dir = tempDir()
            let w = DailyFileWriter(directory: { dir }, now: { date(2026, 10, 15, 12, 0, 0, in: utc) }, timeZone: utc)
            check(w.fileURL(stem: "Log", at: date(2026, 10, 15, 12, 0, 0, in: utc))?.lastPathComponent == "Log-2026-10-15.txt", "file name for a day")
            check(w.fileURL(stem: "LogFile", at: date(2026, 1, 5, 0, 0, 0, in: utc))?.lastPathComponent == "LogFile-2026-01-05.txt", "month and day are zero padded")
            check(w.fileURL(stem: "Log", at: date(2028, 2, 29, 23, 59, 59, in: utc))?.lastPathComponent == "Log-2028-02-29.txt", "leap day")
            check(w.fileURL(stem: "Log", at: date(2026, 12, 31, 23, 59, 59, in: utc))?.lastPathComponent == "Log-2026-12-31.txt", "last day of the year")
        }

        // 2. Lines are appended, each ends in a newline, nothing is overwritten.
        do {
            let dir = tempDir()
            let w = DailyFileWriter(directory: { dir }, now: { date(2026, 10, 15, 12, 0, 0, in: utc) }, timeZone: utc)
            w.append("first", stem: "Log")
            w.append("second", stem: "Log")
            w.append("{LogFile} {json}", stem: "LogFile")
            check(read(dir.appendingPathComponent("Log-2026-10-15.txt")) == "first\nsecond\n", "lines are appended")
            check(read(dir.appendingPathComponent("LogFile-2026-10-15.txt")) == "{LogFile} {json}\n", "the other stream goes to its own file")
            check(try! FileManager.default.contentsOfDirectory(atPath: dir.path).count == 2, "exactly one file per stream")
        }

        // 3. A session that runs past midnight starts the next day's file by itself.
        do {
            let dir = tempDir()
            var clock = date(2026, 10, 15, 23, 59, 59, in: utc)
            let w = DailyFileWriter(directory: { dir }, now: { clock }, timeZone: utc)
            w.append("late", stem: "Log")
            clock = date(2026, 10, 16, 0, 0, 1, in: utc)
            w.append("early", stem: "Log")
            check(read(dir.appendingPathComponent("Log-2026-10-15.txt")) == "late\n", "before midnight stays in the old file")
            check(read(dir.appendingPathComponent("Log-2026-10-16.txt")) == "early\n", "after midnight goes to a new file, same writer")
        }

        // 4. The day is read in the device zone, like DateTime.now() on the Dart side.
        do {
            let dir = tempDir()
            let instant = date(2026, 10, 15, 20, 0, 0, in: utc) // 01:30 on the 16th in Kolkata
            let w = DailyFileWriter(directory: { dir }, now: { instant }, timeZone: kolkata)
            w.append("x", stem: "Log")
            check(FileManager.default.fileExists(atPath: dir.appendingPathComponent("Log-2026-10-16.txt").path), "uses the zone it was given")
            check(!FileManager.default.fileExists(atPath: dir.appendingPathComponent("Log-2026-10-15.txt").path), "not the UTC day")
        }

        // 5. A non-Gregorian calendar or locale must not change the name.
        do {
            let dir = tempDir()
            let w = DailyFileWriter(directory: { dir }, now: { date(2026, 10, 15, 12, 0, 0, in: utc) }, timeZone: utc)
            w.append("x", stem: "Log")
            check(FileManager.default.fileExists(atPath: dir.appendingPathComponent("Log-2026-10-15.txt").path), "Gregorian digits whatever the device locale")
            // Show the failure mode is real when run under -AppleLocale th_TH@calendar=buddhist.
            let naive = DateFormatter()
            naive.dateFormat = "yyyy-MM-dd"
            naive.timeZone = utc
            let naiveName = naive.string(from: date(2026, 10, 15, 12, 0, 0, in: utc))
            print("note: a plain DateFormatter names this day \(naiveName) on this device")
        }

        // 6. A stem cannot leave the logs folder.
        do {
            let dir = tempDir()
            let w = DailyFileWriter(directory: { dir }, now: { date(2026, 10, 15, 12, 0, 0, in: utc) }, timeZone: utc)
            check(DailyFileWriter.safe("../evil") == "_evil", "path traversal is neutralised")
            check(DailyFileWriter.safe("a/b") == "a_b", "a slash becomes an underscore")
            check(DailyFileWriter.safe("") == "Log", "an empty stem falls back to Log")
            check(DailyFileWriter.safe("...") == "Log", "dots only falls back to Log")
            w.append("x", stem: "../../escape")
            check(DailyFileWriter.safe("../../escape") == "_.._escape", "slashes are replaced and leading dots dropped")
            check(try! FileManager.default.contentsOfDirectory(atPath: dir.path) == ["_.._escape-2026-10-15.txt"], "the file stays in the folder")
            check(!FileManager.default.fileExists(atPath: dir.deletingLastPathComponent().appendingPathComponent("escape-2026-10-15.txt").path), "nothing written above the folder")
        }

        // 7. No folder: the write is dropped, nothing crashes.
        do {
            let w = DailyFileWriter(directory: { nil }, now: { Date() }, timeZone: utc)
            w.append("x", stem: "Log")
            check(w.fileURL(stem: "Log", at: Date()) == nil, "no folder means no file URL")
        }

        // 8. Many threads writing at once: no line is lost or split.
        do {
            let dir = tempDir()
            let w = DailyFileWriter(directory: { dir }, now: { date(2026, 10, 15, 12, 0, 0, in: utc) }, timeZone: utc)
            let group = DispatchGroup()
            for t in 0..<8 {
                DispatchQueue.global().async(group: group) {
                    for i in 0..<200 { w.append("thread\(t)-line\(i)", stem: "Log") }
                }
            }
            group.wait()
            let lines = (read(dir.appendingPathComponent("Log-2026-10-15.txt")) ?? "").split(separator: "\n")
            check(lines.count == 1600, "all 1600 lines written, got \(lines.count)")
            check(Set(lines).count == 1600, "no line lost or duplicated")
            check(lines.allSatisfy { $0.hasPrefix("thread") && $0.contains("-line") }, "no line split or interleaved")
        }

        print("\(checks - failures)/\(checks) checks passed")
        exit(failures == 0 ? 0 : 1)
    }
}
