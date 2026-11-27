// 폴더 안 DOCX를 모두 Open XML SDK로 규격 검사한다. 오류가 하나라도 있으면 종료 코드 1.
// 사용: dotnet run --project tools/docx-validate -- <폴더> [Office2007|Office2010|...|Microsoft365]
using System.Text;
using DocumentFormat.OpenXml;
using DocumentFormat.OpenXml.Packaging;
using DocumentFormat.OpenXml.Validation;

if (args.Length < 1 || !Directory.Exists(args[0]))
{
    Console.Error.WriteLine("사용법: DocxValidate <폴더> [Office2007|...|Microsoft365]");
    return 2;
}
string root = Path.GetFullPath(args[0]);
// 기본값 Office2007: 가장 오래된 기준이라 이후 버전에만 있는 요소를 쓰면 바로 걸림 (어느 Word에서나 열리는 범위)
FileFormatVersions version = args.Length > 1 ? Enum.Parse<FileFormatVersions>(args[1]) : FileFormatVersions.Office2007;
const int maxPerFile = 20;

string[] files = Directory.GetFiles(root, "*.docx", SearchOption.AllDirectories)
    .OrderBy(f => f, StringComparer.Ordinal).ToArray();
if (files.Length == 0)
{
    Console.Error.WriteLine($"DOCX가 없음: {root}");
    return 2;
}

var validator = new OpenXmlValidator(version);
var summary = new StringBuilder();
int badFiles = 0, totalErrors = 0;
foreach (string f in files)
{
    string rel = Path.GetRelativePath(root, f).Replace('\\', '/');
    List<string> lines = new();
    try
    {
        using var doc = WordprocessingDocument.Open(f, false);
        var errors = validator.Validate(doc).ToList();
        totalErrors += errors.Count;
        foreach (var e in errors.Take(maxPerFile))
        {
            lines.Add($"{e.Part?.Uri} | {e.Path?.XPath} | {e.Id} | {e.Description}");
        }
        if (errors.Count > maxPerFile)
        {
            lines.Add($"... 외 {errors.Count - maxPerFile}개");
        }
    }
    catch (Exception ex)
    {
        totalErrors++;
        lines.Add($"열지 못함 | {ex.GetType().Name} | {ex.Message}");
    }
    if (lines.Count == 0)
    {
        continue;
    }
    badFiles++;
    Console.WriteLine($"== {rel}");
    lines.ForEach(l => Console.WriteLine("  " + l));
    summary.AppendLine($"### {rel}").AppendLine().AppendLine("| 파트 | 위치 | 오류 |").AppendLine("|---|---|---|");
    foreach (string l in lines)
    {
        string[] p = l.Split(" | ", 4);
        string where = p.Length > 1 ? p[1] : "";
        string what = string.Join(" ", p.Skip(2));
        summary.AppendLine($"| {Esc(p[0])} | {Esc(where)} | {Esc(what)} |");
    }
    summary.AppendLine();
}

string head = $"DOCX 규격 검사 ({version}): 파일 {files.Length}개, 오류 있는 파일 {badFiles}개, 오류 {totalErrors}개";
Console.WriteLine(head);
string? stepSummary = Environment.GetEnvironmentVariable("GITHUB_STEP_SUMMARY");
if (!string.IsNullOrEmpty(stepSummary))
{
    File.AppendAllText(stepSummary, $"## {head}\n\n{summary}");
}
return badFiles == 0 ? 0 : 1;

static string Esc(string s) => s.Replace("|", "\\|").Replace("\n", " ");
