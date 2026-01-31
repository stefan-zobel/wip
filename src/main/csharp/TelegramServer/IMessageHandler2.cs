using System.Buffers;

namespace TelegramServer;

public interface IMessageHandler2
{
    void OnNext(ReadOnlySequence<byte> message);
}
