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
package tech.beshu.ror.unit.acl.factory.decoders

import com.dimafeng.testcontainers.{ForAllTestContainer, MultipleContainers}
import org.scalatest.Matchers._
import tech.beshu.ror.acl.blocks.rules.LdapAuthRule
import tech.beshu.ror.acl.factory.RawRorConfigBasedCoreFactory.AclCreationError.Reason.{MalformedValue, Message}
import tech.beshu.ror.acl.factory.RawRorConfigBasedCoreFactory.AclCreationError.RulesLevelCreationError
import tech.beshu.ror.utils.LdapContainer

class LdapAuthRuleSettingsTests
  extends BaseRuleSettingsDecoderTest[LdapAuthRule]
    with ForAllTestContainer{

  private val containerLdap1 = new LdapContainer("LDAP1", "/test_example.ldif")
  private val containerLdap2 = new LdapContainer("LDAP2", "/test_example.ldif")

  override val container: MultipleContainers = MultipleContainers(containerLdap1, containerLdap2)

  "An LdapAuthRule" should {
    "be able to be loaded from config" when {
      "there is LDAP service with given name and groups are defined" in {
        assertDecodingSuccess(
          yaml =
            s"""
               |readonlyrest:
               |
               |  access_control_rules:
               |
               |  - name: test_block1
               |    ldap_auth:
               |      name: "ldap1"
               |      groups: ["group3"]
               |
               |  ldaps:
               |
               |  - name: ldap1
               |    host: ${containerLdap1.ldapHost}
               |    port: ${containerLdap1.ldapPort}
               |    ssl_enabled: false
               |    search_user_base_DN: "ou=People,dc=example,dc=com"
               |    search_groups_base_DN: "ou=People,dc=example,dc=com"
               |""".stripMargin,
          assertion = _ => ()
        )
      }
      "auth LDAP rule can have caching declared at rule level" in {
        assertDecodingSuccess(
          yaml =
            s"""
               |readonlyrest:
               |
               |  access_control_rules:
               |
               |  - name: test_block1
               |    ldap_auth:
               |      name: "ldap1"
               |      groups: ["group3"]
               |      cache_ttl: 10 sec
               |
               |  ldaps:
               |
               |  - name: ldap1
               |    host: ${containerLdap1.ldapHost}
               |    port: ${containerLdap1.ldapPort}
               |    ssl_enabled: false
               |    search_user_base_DN: "ou=People,dc=example,dc=com"
               |    search_groups_base_DN: "ou=People,dc=example,dc=com"
               |""".stripMargin,
          assertion = _ => ()
        )
      }
    }
    "not be able to be loaded from config" when {
      "no LDAP service with given name is defined" in {
        assertDecodingFailure(
          yaml =
            s"""
               |readonlyrest:
               |
               |  access_control_rules:
               |
               |  - name: test_block1
               |    ldap_auth:
               |      name: "ldap2"
               |      groups: ["group3"]
               |      cache_ttl_in_seconds: 10
               |
               |  ldaps:
               |
               |  - name: ldap1
               |    host: ${containerLdap1.ldapHost}
               |    port: ${containerLdap1.ldapPort}
               |    ssl_enabled: false
               |    search_user_base_DN: "ou=People,dc=example,dc=com"
               |    search_groups_base_DN: "ou=People,dc=example,dc=com"
               |""".stripMargin,
          assertion = errors => {
            errors should have size 1
            errors.head should be(RulesLevelCreationError(Message("Cannot find LDAP service with name: ldap2")))
          }
        )
      }
      "groups are not defined" in {
        assertDecodingFailure(
          yaml =
            s"""
               |readonlyrest:
               |
               |  access_control_rules:
               |
               |  - name: test_block1
               |    ldap_auth:
               |      name: "ldap1"
               |
               |  ldaps:
               |
               |  - name: ldap1
               |    host: ${containerLdap1.ldapHost}
               |    port: ${containerLdap1.ldapPort}
               |    ssl_enabled: false
               |    search_user_base_DN: "ou=People,dc=example,dc=com"
               |    search_groups_base_DN: "ou=People,dc=example,dc=com"
               |""".stripMargin,
          assertion = errors => {
            errors should have size 1
            errors.head should be(RulesLevelCreationError(MalformedValue(
              """ldap_auth:
                |  name: "ldap1"
                |""".stripMargin)))
          }
        )
      }
      "groups set is empty" in {
        assertDecodingFailure(
          yaml =
            s"""
               |readonlyrest:
               |
               |  access_control_rules:
               |
               |  - name: test_block1
               |    ldap_auth:
               |      name: "ldap1"
               |      groups: []
               |      cache_ttl_in_seconds: 10
               |
               |  ldaps:
               |
               |  - name: ldap1
               |    host: ${containerLdap1.ldapHost}
               |    port: ${containerLdap1.ldapPort}
               |    ssl_enabled: false
               |    search_user_base_DN: "ou=People,dc=example,dc=com"
               |    search_groups_base_DN: "ou=People,dc=example,dc=com"
               |""".stripMargin,
          assertion = errors => {
            errors should have size 1
            errors.head should be(RulesLevelCreationError(Message("Non empty list of groups are required")))
          }
        )
      }
      "LDAP service can only handle authentication, not authorization" in {
        assertDecodingFailure(
          yaml =
            s"""
               |readonlyrest:
               |
               |  access_control_rules:
               |
               |  - name: test_block1
               |    ldap_auth:
               |      name: "ldap1"
               |      groups: ["group1"]
               |
               |  ldaps:
               |
               |  - name: ldap1
               |    host: ${containerLdap1.ldapHost}
               |    port: ${containerLdap1.ldapPort}
               |    ssl_enabled: false
               |    search_user_base_DN: "ou=People,dc=example,dc=com"
               |""".stripMargin,
          assertion = errors => {
            errors should have size 1
            errors.head should be(RulesLevelCreationError(Message("Service: ldap1 cannot be used in 'ldap_auth' rule")))
          }
        )
      }
    }
  }
}
