using System.Net.Sockets;
using System.Text;

namespace Misc;

class SimpleClient
{
    static async Task Main(string[] args)
    {
        string serverIP = "127.0.0.1";
        int port = 11111;

        try
        {
            string test = "PSCd3000001000011<ABC></ABC>"; // 17 bytes header + 11 bytes data

            // Convert input to a byte array
            byte[] data = Encoding.ASCII.GetBytes(test);

            // Connect and keep the client in a 'using' block for safety
            using TcpClient client = new TcpClient();
            await client.ConnectAsync(serverIP, port);

            // Obtain the stream for writing
            using NetworkStream stream = client.GetStream();

            while (true)
            {
                // Send the short byte array
                await stream.WriteAsync(data, 0, data.Length);
                await ReadResponse(stream);
                await Task.Delay(100); // 100 ms pause
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error: {ex.Message}");
        }

        Console.WriteLine("Client disconnected.");
    }


    private static async Task ReadResponse(NetworkStream stream)
    {
        using var ms = new MemoryStream();
        byte[] buffer = new byte[17];
        while (true)
        {
            int bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length);
            if (bytesRead == 0)
            {
                Console.WriteLine("Server closed the connection.");
                break;
            }
            if (bytesRead == buffer.Length)
            {
                // complete response arrived
                break;
            }
            else if (bytesRead < buffer.Length)
            {
                ms.Write(buffer, 0, bytesRead);
            }
            if (ms.Length == buffer.Length)
            {
                // complete response arrived
                // reset ms
                ms.Position = 0;
                break;
            }
        }
    }
}
