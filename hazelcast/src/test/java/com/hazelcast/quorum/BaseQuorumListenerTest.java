/*
* Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
*
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*/

package com.hazelcast.quorum;

import com.hazelcast.config.Config;
import com.hazelcast.config.QuorumConfig;
import com.hazelcast.config.QuorumListenerConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.Member;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

@RunWith(HazelcastSerialClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public abstract class BaseQuorumListenerTest extends HazelcastTestSupport {

    @Test
    public void testQuorumFailureEventFiredWhenNodeCountDropsBelowThreshold() throws Exception {
        final CountDownLatch quorumNotPresent = new CountDownLatch(1);
        final String distributedObjectName = randomString();
        final Config config = addQuorum(new Config(), distributedObjectName, quorumListener(null, quorumNotPresent));
        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory();
        factory.newHazelcastInstance(config);
        factory.newHazelcastInstance();
        factory.newHazelcastInstance().shutdown();
        assertOpenEventually(quorumNotPresent, 15);
    }

    @Test
    public void testQuorumEventsFiredWhenNodeCountBelowThenAboveThreshold() throws Exception {
        final CountDownLatch quorumNotPresent = new CountDownLatch(1);
        final CountDownLatch quorumPresent = new CountDownLatch(1);
        final String distributedObjectName = randomString();
        final Config config = addQuorum(new Config(), distributedObjectName, quorumListener(quorumPresent, quorumNotPresent));
        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(3);
        factory.newHazelcastInstance(config);
        factory.newHazelcastInstance(config);
        assertOpenEventually(quorumNotPresent, 15);

        factory.newHazelcastInstance(config);
        assertOpenEventually(quorumPresent);
    }

    @Test
    public void testDifferentQuorumsGetCorrectEvents() throws Exception {
        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(3);
        final CountDownLatch quorumFailureLatch = new CountDownLatch(2);
        final Config config = new Config();
        addQuorum(config, "fourNode", quorumListener(null, quorumFailureLatch));
        addQuorum(config, "threeNode", quorumListener(null, quorumFailureLatch));
        factory.newHazelcastInstance(config);
        factory.newHazelcastInstance(config);
        assertOpenEventually(quorumFailureLatch);
    }

    @Test
    public void testCustomResolverFiresQuorumFailureEvent() throws Exception {
        final CountDownLatch quorumNotPresent = new CountDownLatch(1);

        final QuorumListenerConfig listenerConfig = new QuorumListenerConfig(quorumListener(null, quorumNotPresent));
        final String distributedObjectName = randomString();
        final String quorumName = randomString();
        final QuorumConfig quorumConfig = new QuorumConfig()
                .setName(quorumName)
                .setEnabled(true)
                .addListenerConfig(listenerConfig)
                .setQuorumFunctionImplementation(new QuorumFunction() {
                    @Override
                    public boolean apply(Collection<Member> members) {
                        return false;
                    }
                });
        final Config config = new Config().addQuorumConfig(quorumConfig);
        addQuorumConfig(config, distributedObjectName, quorumName);
        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory();

        factory.newHazelcastInstance(config);
        factory.newHazelcastInstance();
        assertOpenEventually(quorumNotPresent, 15);

    }

    @Test
    public void testQuorumEventProvidesCorrectMemberListSize() throws Exception {
        final CountDownLatch quorumNotPresent = new CountDownLatch(1);
        final QuorumListenerConfig listenerConfig = new QuorumListenerConfig(new QuorumListener() {
            public void onChange(QuorumEvent quorumEvent) {
                if (!quorumEvent.isPresent()) {
                    final Collection<Member> currentMembers = quorumEvent.getCurrentMembers();
                    assertEquals(2, currentMembers.size());
                    assertEquals(3, quorumEvent.getThreshold());
                    quorumNotPresent.countDown();
                }
            }
        });
        final String distributedObjectName = randomString();
        final String quorumName = randomString();
        final QuorumConfig quorumConfig = new QuorumConfig(quorumName, true, 3)
                .addListenerConfig(listenerConfig);
        final Config config = new Config().addQuorumConfig(quorumConfig);
        addQuorumConfig(config, distributedObjectName, quorumName);

        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(3);
        factory.newHazelcastInstance(config);
        factory.newHazelcastInstance(config);
        assertOpenEventually(quorumNotPresent);
    }


    protected Config addQuorum(Config config, String distributedObjectName, QuorumListener listener) {
        final String quorumName = randomString();
        final QuorumListenerConfig listenerConfig = new QuorumListenerConfig();
        listenerConfig.setImplementation(listener);
        config.addQuorumConfig(new QuorumConfig(quorumName, true, 3).addListenerConfig(listenerConfig));
        addQuorumConfig(config, distributedObjectName, quorumName);
        return config;
    }

    protected QuorumListener quorumListener(final CountDownLatch quorumPresent, final CountDownLatch quorumNotPresent) {
        return new QuorumListener() {
            public void onChange(QuorumEvent quorumEvent) {
                if (quorumEvent.isPresent()) {
                    if (quorumPresent != null) {
                        quorumPresent.countDown();
                    }
                } else {
                    if (quorumNotPresent != null) {
                        quorumNotPresent.countDown();
                    }
                }
            }
        };
    }

    protected abstract void addQuorumConfig(Config config, String distributedObjectName, String quorumName);
}
