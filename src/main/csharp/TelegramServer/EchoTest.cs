using System.Diagnostics;
using System.Text;

namespace TelegramServer;

public class EchoTest
{
    public static void Main_(string[] args)
    {
        var test = new EchoTest();
        test.RunEchoExample();
    }

    public void RunEchoExample()
    {
        long start = Environment.TickCount64;
        QueueSenderImpl? cep = null;
        ServerEndpoint? sep = null;

        try
        {
            // 1. Setup Server
            sep = new ServerEndpoint("EchoServer", 5555);
            sep.RegisterMessageHandler(new EchoHandler());
            sep.StartAsync();

            // 2. Setup Client
            cep = new QueueSenderImpl("EchoClient", "localhost", 5555);

            // Test 1: Loop of 200 small messages
            for (int i = 0; i < 200; ++i)
            {
                var tis = new TestInputStream(30);
                cep.Write(tis);
                byte[]? b0 = cep.Submit();

                Debug.Assert(b0 != null);
                Debug.Assert(tis.LengthMetric == b0.Length);
                Console.WriteLine(Encoding.UTF8.GetString(b0));
            }

            // Test 2: Large message (604 rounds)
            var tisLarge = new TestInputStream(604);
            cep.Write(tisLarge);
            byte[]? b1 = cep.Submit();

            Debug.Assert(b1 != null);
            Debug.Assert(tisLarge.LengthMetric == b1.Length);
            Console.WriteLine(Encoding.UTF8.GetString(b1));

            // Test 3: Multiple writes before one submit
            byte[] b20 = Encoding.UTF8.GetBytes("\nho there! show me the echo ...\n");
            byte[] b21 = Encoding.UTF8.GetBytes("\nho there the second time! again, show me your echo ...\n");
            cep.Write(b20, 0, b20.Length);
            cep.Write(b21, 0, b21.Length);
            byte[]? b3 = cep.Submit();

            Debug.Assert(b3 != null);
            Debug.Assert(b20.Length + b21.Length == b3.Length);
            Console.WriteLine(Encoding.UTF8.GetString(b3));

            // Test 4: Two streams written consecutively
            var tis3 = new TestInputStream(2);
            var tis4 = new TestInputStream(2);
            cep.Write(tis3);
            cep.Write(tis4);
            byte[]? b4 = cep.Submit();

            Debug.Assert(b4 != null);
            Debug.Assert(tis3.LengthMetric + tis4.LengthMetric == b4.Length);
            Console.WriteLine(Encoding.UTF8.GetString(b4));

            // Test 5: Very large consecutive streams
            var tis5 = new TestInputStream(1500);
            var tis6 = new TestInputStream(1500);
            cep.Write(tis5);
            cep.Write(tis6);
            byte[]? b5 = cep.Submit();

            Debug.Assert(b5 != null);
            Debug.Assert(tis5.LengthMetric + tis6.LengthMetric == b5.Length);
            Console.WriteLine(Encoding.UTF8.GetString(b5));
        }
        catch (Exception e)
        {
            Console.WriteLine(e.ToString());
        }
        finally
        {
            cep?.Dispose(); // Equivalent to cep.close()
            sep?.Stop();
        }

        long diff = Environment.TickCount64 - start;
        Console.WriteLine($"\ntook: {diff} ms");
    }
}
