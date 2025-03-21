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

import monix.execution.Scheduler.Implicits.global
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.{Inside, WordSpec}
import tech.beshu.ror.acl.Acl
import tech.beshu.ror.acl.AclHandlingResult.Result
import tech.beshu.ror.acl.blocks.Block
import tech.beshu.ror.acl.domain.IndexName
import tech.beshu.ror.acl.factory.{RawRorConfigBasedCoreFactory, CoreSettings}
import tech.beshu.ror.acl.utils.StaticVariablesResolver
import tech.beshu.ror.mocks.{MockHttpClientsFactory, MockRequestContext}
import tech.beshu.ror.utils.TestsUtils.{BlockContextAssertion, headerFrom, _}
import tech.beshu.ror.utils.{JavaUuidProvider, OsEnvVarsProvider, UuidProvider}

class KibanaIndexAndAccessYamlLoadedAclTests extends WordSpec with MockFactory with Inside with BlockContextAssertion {

  private val factory = {
    implicit val clock: Clock = Clock.systemUTC()
    implicit val uuidProvider: UuidProvider = JavaUuidProvider
    implicit val resolver: StaticVariablesResolver = new StaticVariablesResolver(OsEnvVarsProvider)
    new RawRorConfigBasedCoreFactory
  }
  private val acl: Acl = factory
    .createCoreFrom(
      rorConfigFrom(
        """
          |readonlyrest:
          |
          |  access_control_rules:
          |
          |  - name: "Template Tenancy"
          |    verbosity: error
          |    kibana_access: admin
          |    kibana_index: ".kibana_template"
          |
      """.stripMargin
      ),
      MockHttpClientsFactory
    )
    .map {
      case Left(err) => throw new IllegalStateException(s"Cannot create ACL: $err")
      case Right(CoreSettings(aclEngine, _, _)) => aclEngine
    }
    .runSyncUnsafe()


  "An ACL" when {
    "kibana index and kibana access rules are used" should {
      "allow to proceed" in {
        val request = MockRequestContext.default

        val result = acl.handle(request).runSyncUnsafe()

        result.history should have size 1
        inside(result.handlingResult) { case Result.Allow(blockContext, block) =>
          block.name should be(Block.Name("Template Tenancy"))
          assertBlockContext(
            responseHeaders = Set(headerFrom("x-ror-kibana_access" -> "admin")),
            kibanaIndex = Some(IndexName(".kibana_template"))
          ) {
            blockContext
          }
        }
      }
    }
  }
}
