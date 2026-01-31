using System.Text;

namespace TelegramServer;

public class EchoHandler : IMessageAndReplyHandler
{
    public void OnNext(byte[] message, Stream reply)
    {
        // Equivalent to synchronized (System.out)
        lock (Console.Out)
        {
            Console.Write($"{nameof(EchoHandler)} running on {Thread.CurrentThread.Name ?? "ThreadPoolThread"}");
            Console.WriteLine($" : msg= {Encoding.UTF8.GetString(message)}");
        }

        // Write the message back to the reply stream
        reply.Write(message, 0, message.Length);
        reply.Flush();
    }
}
