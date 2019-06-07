/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.servicedependencies.persistence

import com.google.inject.{Inject, Singleton}
import java.time.LocalDate
import play.api.libs.json.{Json, OFormat}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.servicedependencies.model._

import scala.concurrent.{ExecutionContext, Future}


case class BobbyRulesSummary(
    date: LocalDate
  , summary: Map[SlugInfoFlag, Seq[BobbyRuleViolation]]
  )

object BobbyRulesSummary {
  import ReactiveMongoFormats.localDateFormats
  import play.api.libs.json.{__, Json, JsValue, JsError, Reads}
  import play.api.libs.functional.syntax._

  def format: OFormat[BobbyRulesSummary] = {
    implicit val brvf = BobbyRuleViolation.format

    def f(map: Map[String, Seq[JsValue]]): Map[SlugInfoFlag, Seq[BobbyRuleViolation]] =
    map.map { case (k, v) =>  (SlugInfoFlag.parse(k).getOrElse(sys.error("TODO handle failure")), v.map(_.as[BobbyRuleViolation]))
    case other => sys.error(s"Was1 $other")
    }

    def g(map: Map[SlugInfoFlag, Seq[BobbyRuleViolation]]): Map[String, Seq[JsValue]] =
      map.map { case (k, v) =>  (k.asString, v.map(Json.toJson(_)))
      case other => sys.error(s"Was2 $other")
       }

    ( (__ \ "date"      ).format[LocalDate]
    ~ (__ \ "summary"  ).format[Map[String, Seq[JsValue]]].inmap(f, g)
    )(BobbyRulesSummary.apply _, unlift(BobbyRulesSummary.unapply _))
  }
}

trait BobbyRulesSummaryRepo {

    def add(summary: BobbyRulesSummary): Future[Unit]

    def getLatest: Future[Option[BobbyRulesSummary]]

    // Not time bound yet
    def getHistoric: Future[List[BobbyRulesSummary]]
}

@Singleton
class BobbyRulesSummaryRepoImpl @Inject()(mongo: ReactiveMongoComponent)
  extends ReactiveRepository[BobbyRulesSummary, BSONObjectID](
      collectionName = "bobbyRulesSummary"
    , mongo          = mongo.mongoConnector.db
    , domainFormat   = BobbyRulesSummary.format
    )
  with BobbyRulesSummaryRepo {

    private implicit val brsf = BobbyRulesSummary.format
    import ReactiveMongoFormats.localDateFormats
    import ExecutionContext.Implicits.global

    override def indexes: Seq[Index] =
      Seq(Index(
        Seq("date" -> IndexType.Ascending),
        name   = Some("dateIdx"),
        unique = true))

    def add(summary: BobbyRulesSummary): Future[Unit] =
      collection
        .update(
            selector = Json.obj("date" -> Json.toJson(summary.date))
          , update   = summary
          , upsert   = true
          )
        .map(_ => ())



    def getLatest: Future[Option[BobbyRulesSummary]] =
      find("date" -> Json.toJson(LocalDate.now))
        .map(_.headOption)

    // Not time bound yet
    def getHistoric: Future[List[BobbyRulesSummary]] =
      collection.find(Json.obj())
        .sort(Json.obj("date" -> -1))
        .cursor[BobbyRulesSummary]()
        .collect(maxDocs = -1, Cursor.FailOnError[List[BobbyRulesSummary]]())
}