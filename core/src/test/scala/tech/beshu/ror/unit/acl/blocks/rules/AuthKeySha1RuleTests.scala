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
package tech.beshu.ror.unit.acl.blocks.rules

import tech.beshu.ror.acl.blocks.rules.{AuthKeySha1Rule, BasicAuthenticationRule}
import tech.beshu.ror.acl.domain.Secret


class AuthKeySha1RuleTests extends BasicAuthenticationTestTemplate {

  override protected def ruleName: String = classOf[AuthKeySha1Rule].getSimpleName
  override protected val rule = new AuthKeySha1Rule(BasicAuthenticationRule.Settings(Secret("4338fa3ea95532196849ae27615e14dda95c77b1")))
}

class AuthKeySha1RuleAltSyntaxTests extends BasicAuthenticationTestTemplate {

  override protected def ruleName: String = classOf[AuthKeySha1Rule].getSimpleName
  override protected val rule = new AuthKeySha1Rule(BasicAuthenticationRule.Settings(Secret("logstash:9208e8476a2e8adc584bf2f613842177a39645b4")))
}