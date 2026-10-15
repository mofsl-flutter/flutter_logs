//
//  DailyFileWriter.swift
//
//  Appends log lines to one file per calendar day: `<stem>-yyyy-MM-dd.txt`.
//
//  The file is chosen from the date on EVERY call, so a session that runs past
//  midnight starts the next day's file by itself. (The previous implementation
//  added a FileOutput to `Logger` on each call, but `Logger.addOutput` keeps only
//  one output per type, so the first file opened in a session received every
//  write until the app was restarted.)
//
//  Foundation only, so it can be compiled and tested without Flutter: see
//  ios/Tests/DailyFileWriterTests.swift.
//

import Foundation

final class DailyFileWriter {

    private let directory: () -> URL?
    private let now: () -> Date
    private let formatter: DateFormatter
    private let queue = DispatchQueue(label: "flutter_logs.daily_file_writer")

    /// - Parameters:
    ///   - directory: The folder the files live in. Nil when it is unavailable,
    ///     in which case writes are dropped.
    ///   - now: The clock. Tests inject a fixed one.
    ///   - timeZone: The zone the day is read in. The device's current zone by
    ///     default, so it matches the day the Dart side reads from `DateTime.now()`.
    init(directory: @escaping () -> URL?,
         now: @escaping () -> Date = { Date() },
         timeZone: TimeZone = TimeZone.autoupdatingCurrent) {
        self.directory = directory
        self.now = now
        let f = DateFormatter()
        // A fixed Gregorian calendar and POSIX locale: under a Buddhist or Hijri
        // calendar, or Arabic-Indic digits, "yyyy-MM-dd" would name the file with
        // a different year or other digits, and the Dart side would not find it.
        f.locale = Locale(identifier: "en_US_POSIX")
        f.calendar = Calendar(identifier: .gregorian)
        f.timeZone = timeZone
        f.dateFormat = "yyyy-MM-dd"
        self.formatter = f
    }

    /// The file a line written at [date] goes to, e.g. `Log-2026-10-15.txt`.
    func fileURL(stem: String, at date: Date) -> URL? {
        guard let dir = directory() else { return nil }
        let day = queue.sync { formatter.string(from: date) }
        return dir.appendingPathComponent("\(DailyFileWriter.safe(stem))-\(day)").appendingPathExtension("txt")
    }

    /// Appends [line] and a newline to today's file for [stem], creating it if
    /// needed. Returns after the line is written. Never throws; a failed write
    /// is dropped rather than crashing the app from a logging call.
    func append(_ line: String, stem: String) {
        queue.sync {
            guard let dir = directory() else { return }
            let name = "\(DailyFileWriter.safe(stem))-\(formatter.string(from: now()))"
            let url = dir.appendingPathComponent(name).appendingPathExtension("txt")
            guard let data = (line + "\n").data(using: .utf8) else { return }

            if !FileManager.default.fileExists(atPath: url.path) {
                FileManager.default.createFile(atPath: url.path, contents: nil, attributes: nil)
            }
            guard let handle = FileHandle(forWritingAtPath: url.path) else { return }
            handle.seekToEndOfFile()
            handle.write(data)
            handle.closeFile()
        }
    }

    /// A file-name stem that cannot leave the logs folder: letters, digits,
    /// `_`, `-` and `.` only, and never a leading `.`.
    static func safe(_ stem: String) -> String {
        var cleaned = String(stem.map { c -> Character in
            (c.isASCII && (c.isLetter || c.isNumber)) || c == "_" || c == "-" || c == "." ? c : "_"
        })
        while cleaned.hasPrefix(".") { cleaned.removeFirst() }
        return cleaned.isEmpty ? "Log" : cleaned
    }
}
