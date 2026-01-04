namespace Misc;

public enum ReadStatus
{
    Success,
    Eof,
    Timeout,
    Empty
}

public class ReadResult
{
    public ReadStatus Status { get; }
    public byte[]? Data { get; }

    public int Length => Data?.Length ?? 0;

    public ReadResult(ReadStatus status, byte[]? data = null)
    {
        Status = status;
        Data = data;
    }

    public static ReadResult Success(byte[] data) => new (ReadStatus.Success, data);
    public static ReadResult Eof() => new (ReadStatus.Eof);
    public static ReadResult Timeout(byte[] data) => new (ReadStatus.Timeout, data);
    public static ReadResult Empty() => new (ReadStatus.Empty, []);
}
