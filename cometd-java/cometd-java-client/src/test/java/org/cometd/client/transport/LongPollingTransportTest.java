/*
 * Copyright (c) 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cometd.client.transport;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.cometd.bayeux.Message;
import org.cometd.bayeux.Message.Mutable;
import org.cometd.common.HashMapMessage;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.log.Log;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatchman;
import org.junit.runners.model.FrameworkMethod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LongPollingTransportTest
{
    @Rule
    public final TestWatchman testName = new TestWatchman()
    {
        @Override
        public void starting(FrameworkMethod method)
        {
            super.starting(method);
            Log.info("Running {}.{}", method.getMethod().getDeclaringClass().getName(), method.getName());
        }
    };

    @Test
    public void testType()
    {
        ClientTransport transport = LongPollingTransport.create(null);
        assertEquals("long-polling", transport.getName());
    }

    @Test
    public void testAccept()
    {
        ClientTransport transport = LongPollingTransport.create(null);
        assertTrue(transport.accept("1.0"));
    }

    @Test
    public void testSendWithResponse200() throws Exception
    {
        final long processingTime = 500;
        final ServerSocket serverSocket = new ServerSocket(0);
        final AtomicReference<Exception> serverException = new AtomicReference<Exception>();
        Thread serverThread = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    Socket socket = serverSocket.accept();

                    Thread.sleep(processingTime);

                    OutputStream output = socket.getOutputStream();
                    output.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Connection: close\r\n" +
                                    "Content-Type: application/json;charset=UTF-8\r\n" +
                                    "Content-Length: 2\r\n" +
                                    "\r\n" +
                                    "[]").getBytes("UTF-8"));
                    output.flush();
                    socket.close();
                }
                catch (Exception x)
                {
                    serverException.set(x);
                }
            }
        };
        serverThread.start();
        final String serverURL = "http://localhost:" + serverSocket.getLocalPort();

        try
        {
            HttpClient httpClient = new HttpClient();
            httpClient.start();

            try
            {
                final CountDownLatch latch = new CountDownLatch(1);
                HttpClientTransport transport = new LongPollingTransport(null, httpClient);
                transport.setURL(serverURL);
                transport.init();

                long start = System.nanoTime();
                transport.send(new EmptyTransportListener()
                {
                    @Override
                    public void onMessages(List<Message.Mutable> messages)
                    {
                        latch.countDown();
                    }
                }, new HashMapMessage());
                long end = System.nanoTime();

                long elapsed = TimeUnit.NANOSECONDS.toMillis(end - start);
                assertTrue("elapsed=" + elapsed + ", processing=" + processingTime, elapsed <= processingTime);
                assertTrue(latch.await(2 * processingTime, TimeUnit.MILLISECONDS));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                httpClient.stop();
            }
        }
        finally
        {
            serverThread.join();
            assertNull(serverException.get());
            serverSocket.close();
        }
    }

    @Test
    public void testSendWithResponse500() throws Exception
    {
        final long processingTime = 500;
        final ServerSocket serverSocket = new ServerSocket(0);
        final AtomicReference<Exception> serverException = new AtomicReference<Exception>();
        Thread serverThread = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    Socket socket = serverSocket.accept();

                    Thread.sleep(processingTime);

                    OutputStream output = socket.getOutputStream();
                    output.write((
                            "HTTP/1.1 500 Internal Server Error\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n").getBytes("UTF-8"));
                    output.flush();

                    socket.close();
                }
                catch (Exception x)
                {
                    serverException.set(x);
                }
            }
        };
        serverThread.start();
        String serverURL = "http://localhost:" + serverSocket.getLocalPort();

        try
        {
            HttpClient httpClient = new HttpClient();
            httpClient.start();

            try
            {
                HttpClientTransport transport = new LongPollingTransport(null, httpClient);
                final CountDownLatch latch = new CountDownLatch(1);
                transport.setURL(serverURL);
                transport.init();

                long start = System.nanoTime();
                transport.send(new EmptyTransportListener()
                {
                    @Override
                    public void onProtocolError(String info, Message[] messages)
                    {
                        latch.countDown();
                    }
                });
                long end = System.nanoTime();

                long elapsed = TimeUnit.NANOSECONDS.toMillis(end - start);
                assertTrue("elapsed=" + elapsed + ", processing=" + processingTime, elapsed <= processingTime);
                assertTrue(latch.await(2000 + 2 * processingTime, TimeUnit.MILLISECONDS));
            }
            finally
            {
                httpClient.stop();
            }
        }
        finally
        {
            serverThread.join();
            assertNull(serverException.get());
            serverSocket.close();
        }
    }

    @Test
    public void testSendWithServerDown() throws Exception
    {
        ServerSocket serverSocket = new ServerSocket(0);
        serverSocket.close();
        final String serverURL = "http://localhost:" + serverSocket.getLocalPort();

        HttpClient httpClient = new HttpClient();
        httpClient.start();

        try
        {
            HttpClientTransport transport = new LongPollingTransport(null, httpClient);
            final CountDownLatch latch = new CountDownLatch(1);
            transport.setURL(serverURL);
            transport.init();

            transport.send(new EmptyTransportListener()
            {
                @Override
                public void onConnectException(Throwable x, Message[] messages)
                {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
        }
        finally
        {
            httpClient.stop();
        }
    }

    @Test
    public void testSendWithServerCrash() throws Exception
    {
        final long processingTime = 500;
        final ServerSocket serverSocket = new ServerSocket(0);
        final AtomicReference<Exception> serverException = new AtomicReference<Exception>();
        Thread serverThread = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    Socket socket = serverSocket.accept();

                    Thread.sleep(processingTime);

                    socket.close();
                }
                catch (Exception x)
                {
                    serverException.set(x);
                }
            }
        };
        serverThread.start();
        final String serverURL = "http://localhost:" + serverSocket.getLocalPort();

        try
        {
            HttpClient httpClient = new HttpClient();
            httpClient.start();

            try
            {
                HttpClientTransport transport = new LongPollingTransport(null, httpClient);
                final CountDownLatch latch = new CountDownLatch(1);
                transport.setURL(serverURL);
                transport.init();

                long start = System.nanoTime();
                transport.send(new EmptyTransportListener()
                {
                    @Override
                    public void onException(Throwable x, Message[] messages)
                    {
                        latch.countDown();
                    }
                });
                long end = System.nanoTime();

                long elapsed = TimeUnit.NANOSECONDS.toMillis(end - start);
                assertTrue("elapsed=" + elapsed + ", processing=" + processingTime, elapsed <= processingTime);
                assertTrue(latch.await(2 * processingTime, TimeUnit.MILLISECONDS));
            }
            finally
            {
                httpClient.stop();
            }
        }
        finally
        {
            serverThread.join();
            assertNull(serverException.get());
            serverSocket.close();
        }
    }

    @Test
    public void testSendWithServerExpire() throws Exception
    {
        final long timeout = 1000;
        final ServerSocket serverSocket = new ServerSocket(0);
        final AtomicReference<Exception> serverException = new AtomicReference<Exception>();
        Thread serverThread = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    Socket socket = serverSocket.accept();

                    Thread.sleep(2 * timeout);

                    OutputStream output = socket.getOutputStream();
                    output.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Connection: close\r\n" +
                                    "Content-Type: application/json;charset=UTF-8\r\n" +
                                    "Content-Length: 2\r\n" +
                                    "\r\n" +
                                    "[]").getBytes("UTF-8"));
                    output.flush();

                    socket.close();
                }
                catch (Exception x)
                {
                    serverException.set(x);
                }
            }
        };
        serverThread.start();
        final String serverURL = "http://localhost:" + serverSocket.getLocalPort();

        try
        {
            HttpClient httpClient = new HttpClient();
            httpClient.setTimeout(timeout);
            httpClient.start();

            try
            {
                HttpClientTransport transport = new LongPollingTransport(null, httpClient);
                final CountDownLatch latch = new CountDownLatch(1);
                transport.setURL(serverURL);
                transport.init();

                transport.send(new EmptyTransportListener()
                {
                    @Override
                    public void onExpire(Message[] messages)
                    {
                        latch.countDown();
                    }
                });

                assertTrue(latch.await(2 * timeout, TimeUnit.MILLISECONDS));
            }
            finally
            {
                httpClient.stop();
            }
        }
        finally
        {
            serverThread.join();
            assertNull(serverException.get());
            serverSocket.close();
        }
    }

    private class EmptyTransportListener implements TransportListener
    {
        public void onSending(Message[] messages)
        {
        }

        public void onMessages(List<Mutable> metaMessages)
        {
        }

        public void onConnectException(Throwable x, Message[] messages)
        {
        }

        public void onException(Throwable x, Message[] messages)
        {
        }

        public void onExpire(Message[] messages)
        {
        }

        public void onProtocolError(String info, Message[] messages)
        {
        }
    }
}
