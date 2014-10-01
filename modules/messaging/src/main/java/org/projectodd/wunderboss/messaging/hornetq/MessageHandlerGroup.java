/*
 * Copyright 2014 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.projectodd.wunderboss.messaging.hornetq;

import org.jboss.logging.Logger;
import org.projectodd.wunderboss.Options;
import org.projectodd.wunderboss.codecs.Codecs;
import org.projectodd.wunderboss.messaging.Connection;
import org.projectodd.wunderboss.messaging.Destination.ListenOption;
import org.projectodd.wunderboss.messaging.Listener;
import org.projectodd.wunderboss.messaging.MessageHandler;
import org.projectodd.wunderboss.messaging.Session;

import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MessageHandlerGroup implements Listener {

    public MessageHandlerGroup(HornetQConnection connection,
                               MessageHandler handler,
                               Codecs codecs,
                               HornetQDestination destination,
                               Options<ListenOption> options) {
        this.connection = connection;
        this.handler = handler;
        this.codecs = codecs;
        this.destination = destination;
        this.options = options;
    }

    public synchronized MessageHandlerGroup start() throws Exception {
        if (!this.started) {
            int concurrency = this.options.getInt(ListenOption.CONCURRENCY);
            while(concurrency-- > 0) {
                HornetQSession session = createSession();
                listeners.add(new JMSListener(this.handler,
                                              this.codecs,
                                              this.destination,
                                              this.connection,
                                              session,
                                              createConsumer(session.context()))
                                      .start());
            }

            this.started = true;
        }

        return this;
    }

    @Override
    public synchronized void close() throws Exception {
        if (this.started) {
            for(JMSListener each : this.listeners) {
                each.stop();
            }

            this.listeners.clear();

            this.started = false;
        }
    }

    protected HornetQSession createSession() throws Exception {
       // if (isTransacted()) {
         //   return null; //((XAConnection)this.connection.jmsContext()).createXASession();
        //} else {
            // Use local transactions for non-XA message processors
            //return
            //this.connection.jmsContext().getSession(true,
            //Session.SESSION_TRANSACTED);

            return (HornetQSession)this.connection.createSession(new HashMap<Connection.CreateSessionOption, Object>() {{
                put(Connection.CreateSessionOption.MODE, isTransacted() ? Session.Mode.TRANSACTED : Session.Mode.AUTO_ACK);
            }});
        //}
    }

    protected JMSConsumer createConsumer(JMSContext context) throws JMSException {
        String selector = this.options.getString(ListenOption.SELECTOR);
        javax.jms.Destination destination = this.destination.jmsDestination();

        return context.createConsumer(destination, selector);
    }

    protected boolean isTransacted() {
        return this.options.getBoolean(ListenOption.TRANSACTED);
    }

    private final MessageHandler handler;
    private final Codecs codecs;
    private final HornetQDestination destination;
    private final Options<ListenOption> options;
    private HornetQConnection connection;
    private final List<JMSListener> listeners = new ArrayList<>();
    private boolean started = false;

    private static final Logger log = Logger.getLogger("org.projectodd.wunderboss.messaging");
}
