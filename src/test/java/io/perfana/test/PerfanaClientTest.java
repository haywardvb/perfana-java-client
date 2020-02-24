/**
 * Perfana Java Client - Java library that talks to the Perfana server
 * Copyright (C) 2020  Peter Paul Bakker @ Stokpop, Daniel Moll @ Perfana.io
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.perfana.test;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.perfana.client.PerfanaClient;
import io.perfana.client.PerfanaClientBuilder;
import io.perfana.client.api.PerfanaClientLogger;
import io.perfana.client.api.PerfanaClientLoggerStdOut;
import io.perfana.client.api.PerfanaConnectionSettings;
import io.perfana.client.api.PerfanaConnectionSettingsBuilder;
import io.perfana.client.api.TestContext;
import io.perfana.client.api.TestContextBuilder;
import nl.stokpop.eventscheduler.exception.KillSwitchException;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Collections;
import java.util.Arrays;


import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * This test class is in another package to check access package private fields.
 */
public class PerfanaClientTest
{
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());

    @Test
    public void create() {

        PerfanaClient client = createPerfanaClient();

        assertNotNull(client);

//        client.startSession();
//        client.stopSession();
    }

    private PerfanaClient createPerfanaClient() {
        PerfanaClientLogger testLogger = new PerfanaClientLoggerStdOut();

        String eventSchedule =
                "   \n" +
                "    PT1S  |restart   (   restart to reset replicas  )   |{ 'server':'myserver' 'replicas':2, 'tags': [ 'first', 'second' ] }    \n" +
                "PT600S   |scale-down |   { 'replicas':1 }   \n" +
                "PT660S|    heapdump|server=    myserver.example.com;   port=1567  \n" +
                "   PT900S|scale-up|{ 'replicas':2 }\n" +
                "  \n";

        PerfanaConnectionSettings settings = new PerfanaConnectionSettingsBuilder()
                .setPerfanaUrl("http://localhost:" + wireMockRule.port())
                .setRetryMaxCount("5")
                .setRetryTimeInSeconds("3")
                .build();

        TestContext context = new TestContextBuilder()
                .setTestType("testType")
                .setTestEnvironment("testEnv")
                .setTestRunId("testRunId")
                .setCIBuildResultsUrl("http://url")
                .setApplicationRelease("release")
                .setRampupTimeInSeconds("10")
                .setConstantLoadTimeInSeconds("300")
                .setAnnotations("annotation")
                .setVariables(Collections.emptyMap())
                .setTags("")
                .build();

        return new PerfanaClientBuilder()
                .setPerfanaConnectionSettings(settings)
                .setTestContext(context)
                .setAssertResultsEnabled(true)
                .addEventProperty("myClass", "name", "value")
                .setCustomEvents(eventSchedule)
                .setLogger(testLogger)
                .build();
    }

    /**
     * Regression: no exceptions expected feeding null
     */
    @Test
    public void createWithNulls() {

        TestContext context = new TestContextBuilder()
                .setAnnotations(null)
                .setApplicationRelease(null)
                .setApplication(null)
                .setCIBuildResultsUrl(null)
                .setConstantLoadTimeInSeconds(null)
                .setConstantLoadTime(null)
                .setRampupTimeInSeconds(null)
                .setRampupTime(null)
                .setTestEnvironment(null)
                .setTestRunId(null)
                .setTestType(null)
                .setVariables((Properties)null)
                .setTags((String)null)
                .build();

        PerfanaConnectionSettings settings = new PerfanaConnectionSettingsBuilder()
                .setPerfanaUrl(null)
                .setRetryMaxCount(null)
                .setRetryTimeInSeconds(null)
                .setKeepAliveInterval(null)
                .setKeepAliveTimeInSeconds(null)
                .setRetryDuration(null).build();

        new PerfanaClientBuilder()
                .setTestContext(context)
                .setPerfanaConnectionSettings(settings)
                .setCustomEvents(null)
                .setBroadcaster(null)
                .build();

    }

    @Test
    public void createWithFail() {

        PerfanaConnectionSettings settings =
                new PerfanaConnectionSettingsBuilder()
                        .setRetryTimeInSeconds("P5")
                        .build();

        assertNotNull(settings);

    }

    @Test
    public void createJsonMessage() {
        Map<String, String> vars = new HashMap<>();

        String var1 = "hostname";
        String value1 = "foo.com";
        vars.put(var1, value1);

        String var2 = "env";
        String value2 = "performance-test-2-env";

        String tag1 = "   tag-1 ";
        String tag2 = " tag-2  ";
        String tags = String.join(",", Arrays.asList(tag1, tag2));
        vars.put(var2, value2);

        String annotations = "Xmx set to 2g";
        TestContext context = new TestContextBuilder()
                .setAnnotations(annotations)
                .setVariables(vars)
                .setTags(tags)
                .build();

        String json = PerfanaClient.perfanaMessageToJson(context, false);

        assertTrue(json.contains(annotations));
        assertTrue(json.contains(var1));
        assertTrue(json.contains(var2));
        assertTrue(json.contains(value1));
        assertTrue(json.contains(value2));
        assertTrue(json.contains(tag1.trim()));
        assertTrue(json.contains(tag2.trim()));

    }

    @Test
    public void testVarsMissing() {
        /*
         * <variables>
         *    <property>
         *        <name>foo</name>
         *        <value>1</value>
         *    </property>
         *    <property>
         *        <name>bar</name>
         *        <value>2</value>
         *    </property>
         * </variables>
         */
        Properties props = new Properties();
        props.put("foo", "foo-1");
        props.put("bar", "bar-2");
        
        TestContext context = new TestContextBuilder()
                .setVariables(props)
                .build();

        String json = PerfanaClient.perfanaMessageToJson(context, false);
        assertTrue(json.contains("foo"));
        assertTrue(json.contains("bar"));
        assertTrue(json.contains("foo-1"));
        assertTrue(json.contains("bar-2"));
    }

    @Test(expected = KillSwitchException.class)
    public void testPerfanaTestCallWithResult() {
        wireMockRule.stubFor(post(urlEqualTo("/test"))
                .willReturn(aResponse()
                        .withBody("{ abort: true, test-results: [ ] }")));

        PerfanaClient perfanaClient = createPerfanaClient();
        TestContext testContext = new TestContextBuilder().build();
        perfanaClient.callPerfanaTestEndpoint(testContext, false);
    }
}
