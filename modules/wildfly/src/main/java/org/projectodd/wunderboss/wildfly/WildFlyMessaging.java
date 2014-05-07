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

package org.projectodd.wunderboss.wildfly;

import org.hornetq.jms.server.JMSServerManager;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.projectodd.wunderboss.Options;
import org.projectodd.wunderboss.WunderBoss;
import org.projectodd.wunderboss.messaging.hornetq.HornetQEndpoint;
import org.projectodd.wunderboss.messaging.hornetq.HornetQMessaging;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Map;

public class WildFlyMessaging extends HornetQMessaging {

    static final ServiceName JMS_MANAGER_SERVICE_NAME = ServiceName.JBOSS.append("messaging", "default", "jms", "manager");

    public WildFlyMessaging(String name, Options<CreateOption> options) {
        super(name, options);
        try {
            context = new InitialContext();
        } catch (NamingException ex) {
            // TODO: something better
            ex.printStackTrace();
        }
    }

    @Override
    public synchronized void start() throws Exception {
        if (!started) {
            ServiceRegistry serviceRegistry = (ServiceRegistry) WunderBoss.options().get("service-registry");
            ServiceName hornetQServiceName = JMS_MANAGER_SERVICE_NAME;
            jmsServerManager = (JMSServerManager) serviceRegistry.getRequiredService(hornetQServiceName).getValue();
            started = true;
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        if (started) {
            jmsServerManager = null;
            started = false;
        }
    }

    @Override
    public synchronized HornetQEndpoint findOrCreateEndpoint(String name,
                                                      Map<CreateEndpointOption, Object> options) throws Exception {
        HornetQEndpoint endpoint = super.findOrCreateEndpoint(name, options);
        // TODO: Should we be smarter and destroy endpoints we created but not ones created
        // by something else? Or maybe close is the wrong name for what close does - it would
        // be a problem if an embedded app called .close on the endpoint in one place while still
        // using it in another as well.
        endpoint.destroyOnClose(false);
        return endpoint;
    }

    @Override
    protected Object lookupJNDI(String jndiName) {
        try {
            return context.lookup(jndiName);
        } catch (NamingException ex) {
            // TODO: something better
            ex.printStackTrace();
        }
        return null;
    }

    private InitialContext context;
}