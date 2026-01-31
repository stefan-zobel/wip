namespace TelegramServer;

public class TestInputStream : Stream
{
    private static readonly byte[] PrintableChars;

    static TestInputStream()
    {
        PrintableChars = new byte[127 - 32];
        for (int i = 32; i < 127; i++)
        {
            PrintableChars[i - 32] = (byte)i;
        }
    }

    private int _currentPos = 0;
    private int _currentRound = 1;
    private readonly int _maxRounds;

    public TestInputStream(int repeatCount)
    {
        _maxRounds = repeatCount < 1 ? 1 : repeatCount;
    }

    /// <summary>
    /// Returns a single byte as an int.
    /// </summary>
    public override int ReadByte()
    {
        if (_currentPos >= PrintableChars.Length)
        {
            if (_currentRound < _maxRounds)
            {
                _currentPos = 0;
                _currentRound++;
            }
            else
            {
                return -1;
            }
        }
        return PrintableChars[_currentPos++];
    }

    /// <summary>
    /// Standard .NET Read implementation redirecting to ReadByte logic.
    /// </summary>
    public override int Read(byte[] buffer, int offset, int count)
    {
        int totalRead = 0;
        while (totalRead < count)
        {
            int b = ReadByte();
            if (b == -1) break;
            buffer[offset + totalRead] = (byte)b;
            totalRead++;
        }
        return totalRead;
    }

    public long LengthMetric => (long)_maxRounds * PrintableChars.Length;

    // Required Stream Overrides
    public override bool CanRead => true;
    public override bool CanSeek => false;
    public override bool CanWrite => false;
    public override long Length => LengthMetric;
    public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
    public override void Flush() { }
    public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
    public override void SetLength(long value) => throw new NotSupportedException();

    public override void Write(byte[] buffer, int offset, int count)
    {
        throw new NotImplementedException();
    }
}
