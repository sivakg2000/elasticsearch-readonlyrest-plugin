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
package tech.beshu.ror.integration

import java.time.Clock

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import monix.execution.Scheduler.Implicits.global
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.{Inside, WordSpec}
import tech.beshu.ror.acl.Acl
import tech.beshu.ror.acl.AclHandlingResult.Result
import tech.beshu.ror.acl.blocks.Block
import tech.beshu.ror.acl.domain.{LoggedUser, User}
import tech.beshu.ror.acl.factory.{AsyncHttpClientsFactory, RawRorConfigBasedCoreFactory, CoreSettings}
import tech.beshu.ror.acl.utils.StaticVariablesResolver
import tech.beshu.ror.mocks.MockRequestContext
import tech.beshu.ror.utils.TestsUtils._
import tech.beshu.ror.utils.{JavaUuidProvider, OsEnvVarsProvider, UuidProvider}

class RorKbnAuthYamlLoadedAclTests extends WordSpec with MockFactory with Inside with BlockContextAssertion {
  private val factory = {
    implicit val clock: Clock = Clock.systemUTC()
    implicit val uuidProvider: UuidProvider = JavaUuidProvider
    implicit val resolver: StaticVariablesResolver = new StaticVariablesResolver(OsEnvVarsProvider)
    new RawRorConfigBasedCoreFactory
  }
  private val acl: Acl = factory
    .createCoreFrom(
      rorConfigFrom(
        """http.bind_host: _eth0:ipv4_
          |network.host: _eth0:ipv4_
          |
          |http.type: ssl_netty4
          |#transport.type: local
          |
          |readonlyrest:
          |  ssl:
          |    enable: true
          |    keystore_file: "keystore.jks"
          |    keystore_pass: readonlyrest
          |    key_pass: readonlyrest
          |
          |  access_control_rules:
          |    - name: Container housekeeping is allowed
          |      type: allow
          |      auth_key: admin:container
          |
          |    - name: Valid JWT token is present
          |      type: allow
          |      ror_kbn_auth:
          |        name: "kbn1"
          |
          |    - name: Valid JWT token is present with another key
          |      type: allow
          |      ror_kbn_auth:
          |        name: "kbn2"
          |
          |    - name: Valid JWT token is present with a third key + role
          |      type: allow
          |      ror_kbn_auth:
          |        name: "kbn3"
          |        roles: ["viewer_group"]
          |
          |  ror_kbn:
          |
          |    - name: kbn1
          |      signature_key: "123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456"
          |
          |    - name: kbn2
          |      signature_key: "123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456"
          |
          |    - name: kbn3
          |      signature_key: "1234567890.1234567890.1234567890.1234567890.1234567890.1234567890.1234567890.1234567890.1234567890.1234567890.1234567890.1234567890.1234567890.1234567890.1234567890.1234567890.1234567890"
          |
    """.stripMargin
      ),
      new AsyncHttpClientsFactory
    )
    .map {
      case Left(err) => throw new IllegalStateException(s"Cannot create ACL: $err")
      case Right(CoreSettings(aclEngine, _, _)) => aclEngine
    }
    .runSyncUnsafe()

  "A ACL" when {
    "is configured using config above" should {
      "allow to proceed" when {
        "JWT token with empty list of groups is defined" in {
          val jwtBuilder = Jwts.builder
            .signWith(Keys.hmacShaKeyFor("123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456.123456".getBytes))
            .setSubject("test")
            .claim("groups", "")
            .claim("user", "user")
          val request = MockRequestContext.default.copy(headers = Set(header("Authorization", s"Bearer ${jwtBuilder.compact}")))

          val result = acl.handle(request).runSyncUnsafe()

          result.history should have size 2
          inside(result.handlingResult) { case Result.Allow(blockContext, block) =>
            block.name should be(Block.Name("Valid JWT token is present"))
            assertBlockContext(loggedUser = Some(LoggedUser(User.Id("user")))) {
              blockContext
            }
          }
        }
      }
    }
  }
}
