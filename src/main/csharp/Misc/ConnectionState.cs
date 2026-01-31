namespace Misc;

// This could potentially be used for bookkeeping ?
internal class ConnectionState
{
    // The current number of units on this connection
    private int _unitCount;

    // Using 1 for true, 0 for false to allow Interlocked operations
    private int _firstMessageAlreadySent;

    public int UnitCount => _unitCount;
    public bool HasSentMessage => _firstMessageAlreadySent == 1;

    public void Increment() => Interlocked.Increment(ref _unitCount);

    public void Decrement() => Interlocked.Decrement(ref _unitCount);

    // Call this when the first message is received
    public void MarkMessageReceived() => Interlocked.Exchange(ref _firstMessageAlreadySent, 1);

    public bool IsEmpty => _unitCount == 0;

    public bool AreUnitsConnected => !HasSentMessage || !IsEmpty;
}
