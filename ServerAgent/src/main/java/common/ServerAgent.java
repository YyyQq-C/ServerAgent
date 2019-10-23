package common;

import java.lang.instrument.Instrumentation;

/**
 * Created by YongQc
 *
 * 2019-10-23 10:06.
 *
 * ServerAgent
 */
public class ServerAgent
{
    private static Instrumentation instrumentation;
    private static Object lock = new Object();

    public static Instrumentation getInstrumentation()
    {
        return instrumentation;
    }

    public static void agentmain(String args, Instrumentation ins)
    {
        synchronized (lock)
        {
            if (instrumentation == null)
            {
                instrumentation = ins;
            }
        }
    }
}
