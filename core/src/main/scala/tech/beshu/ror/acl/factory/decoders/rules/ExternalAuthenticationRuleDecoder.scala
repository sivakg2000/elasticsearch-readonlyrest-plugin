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
package tech.beshu.ror.acl.factory.decoders.rules

import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import io.circe.Decoder
import tech.beshu.ror.acl.blocks.definitions.{CacheableExternalAuthenticationServiceDecorator, ExternalAuthenticationService}
import tech.beshu.ror.acl.blocks.rules.ExternalAuthenticationRule
import tech.beshu.ror.acl.blocks.rules.ExternalAuthenticationRule.Settings
import tech.beshu.ror.acl.factory.RawRorConfigBasedCoreFactory.AclCreationError
import tech.beshu.ror.acl.factory.RawRorConfigBasedCoreFactory.AclCreationError.Reason.Message
import tech.beshu.ror.acl.factory.RawRorConfigBasedCoreFactory.AclCreationError.RulesLevelCreationError
import tech.beshu.ror.acl.factory.decoders.definitions.{Definitions, ExternalAuthenticationServicesDecoder}
import tech.beshu.ror.acl.factory.decoders.definitions.ExternalAuthenticationServicesDecoder._
import tech.beshu.ror.acl.factory.decoders.rules.RuleBaseDecoder.RuleDecoderWithoutAssociatedFields
import tech.beshu.ror.acl.factory.decoders.rules.ExternalAuthenticationRuleDecoder._
import tech.beshu.ror.acl.show.logs._
import tech.beshu.ror.acl.utils.CirceOps._

import scala.concurrent.duration.FiniteDuration
import tech.beshu.ror.acl.factory.decoders.common._
import tech.beshu.ror.acl.utils.SyncDecoderCreator

class ExternalAuthenticationRuleDecoder(authenticationServices: Definitions[ExternalAuthenticationService])
  extends RuleDecoderWithoutAssociatedFields[ExternalAuthenticationRule](
    simpleExternalAuthenticationServiceNameAndLocalConfig
      .orElse(complexExternalAuthenticationServiceNameAndLocalConfig)
      .toSyncDecoder
      .emapE {
        case (name, Some(ttl)) =>
          findAuthenticationService(authenticationServices.items, name)
            .map(new CacheableExternalAuthenticationServiceDecorator(_, ttl))
        case (name, None) =>
          findAuthenticationService(authenticationServices.items, name)
      }
      .map(service => new ExternalAuthenticationRule(Settings(service)))
      .decoder
  )

object ExternalAuthenticationRuleDecoder {

  private def simpleExternalAuthenticationServiceNameAndLocalConfig: Decoder[(ExternalAuthenticationService.Name, Option[FiniteDuration Refined Positive])] =
    ExternalAuthenticationServicesDecoder
      .serviceNameDecoder
      .map((_, None))

  private def complexExternalAuthenticationServiceNameAndLocalConfig: Decoder[(ExternalAuthenticationService.Name, Option[FiniteDuration Refined Positive])] = {
    SyncDecoderCreator
      .instance { c =>
        for {
          name <- c.downField("service").as[ExternalAuthenticationService.Name]
          ttl <- c.downFields("cache_ttl_in_sec", "cache_ttl").as[FiniteDuration Refined Positive]
        } yield (name, Option(ttl))
      }
      .mapError(RulesLevelCreationError.apply)
      .decoder
  }

  private def findAuthenticationService(authenticationServices: List[ExternalAuthenticationService],
                                        searchedServiceName: ExternalAuthenticationService.Name): Either[AclCreationError, ExternalAuthenticationService] = {
    authenticationServices.find(_.id === searchedServiceName) match {
      case Some(service) => Right(service)
      case None => Left(RulesLevelCreationError(Message(s"Cannot find external authentication service with name: ${searchedServiceName.show}")))
    }
  }
}