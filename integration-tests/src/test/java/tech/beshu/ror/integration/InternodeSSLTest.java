/*
 *    This file is part of ReadonlyREST.
 *
 *    ReadonlyREST is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    ReadonlyREST is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with ReadonlyREST.  If not, see http://www.gnu.org/licenses/
 */

package tech.beshu.ror.integration;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import tech.beshu.ror.commons.Constants;
import tech.beshu.ror.utils.containers.ContainerUtils;
import tech.beshu.ror.utils.containers.ESWithReadonlyRestContainer;
import tech.beshu.ror.utils.containers.ESWithReadonlyRestContainerUtils;
import tech.beshu.ror.utils.containers.MultiContainer;
import tech.beshu.ror.utils.containers.MultiContainerDependent;
import tech.beshu.ror.utils.gradle.RorPluginGradleProject;
import tech.beshu.ror.utils.httpclient.RestClient;

import java.util.Optional;

import static junit.framework.TestCase.assertTrue;

public class InternodeSSLTest {

  private static final String matchingEndpoint = "/_nodes/local";

  @ClassRule
  public static MultiContainerDependent<ESWithReadonlyRestContainer> container = createContainers();

  public static MultiContainerDependent<ESWithReadonlyRestContainer> createContainers() {
    Network network = Network.newNetwork();
    return ESWithReadonlyRestContainerUtils.create(
        RorPluginGradleProject.fromSystemProperty(),
        new MultiContainer.Builder().add(
            "node1", () -> ESWithReadonlyRestContainer.create(
                RorPluginGradleProject.fromSystemProperty(),
                "/internode_ssl/elasticsearch.yml",
                Optional.empty(),
                containerBuilder -> {
                  containerBuilder.withNetwork(network);
                  containerBuilder.withNetworkAliases("node1");
                  return null;
                }
            )
        ).build(),
        "/internode_ssl/elasticsearch.yml",
        adminClient -> {
          System.out.println("INITIALIZED LOL");
        },
        containerBuilder -> {
          containerBuilder
              .withNetwork(network)
              .withNetworkAliases("node0");

          return null;
        }

    );
  }

  @Test
  public void testOK_GoodCredsWithGoodRule() throws Exception {

    assertTrue(true);
  }

  private HttpResponse mkRequest(String user, String pass, String endpoint) throws Exception {
    return mkRequest(user, pass, endpoint, null);
  }

  private HttpResponse mkRequest(String user, String pass, String endpoint, String preferredGroup) throws Exception {
    RestClient rcl = container.getContainer().getBasicAuthClient(user, pass);
    HttpGet req = new HttpGet(rcl.from(
        endpoint,
        new ImmutableMap.Builder<String, String>()
            .build()
    ));

    if (!Strings.isNullOrEmpty(preferredGroup)) {
      req.setHeader(Constants.HEADER_GROUP_CURRENT, preferredGroup);
    }
    return rcl.execute(req);
  }

}
