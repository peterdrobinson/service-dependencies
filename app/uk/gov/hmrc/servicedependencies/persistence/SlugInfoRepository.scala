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
import com.mongodb.BasicDBObject
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.ReplaceOptions
import org.mongodb.scala.model.Updates._
import uk.gov.hmrc.mongo.component.MongoComponent
import uk.gov.hmrc.servicedependencies.model._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlugInfoRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends SlugInfoRepositoryBase[SlugInfo](
      mongo,
      domainFormat   = MongoSlugInfoFormats.slugInfoFormat
    ) {

  def add(slugInfo: SlugInfo): Future[Boolean] =
    collection
      .replaceOne(
        filter = equal("uri", slugInfo.uri),
        slugInfo,
        ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_.wasAcknowledged())

  def getAllEntries: Future[Seq[SlugInfo]] = collection.find().toFuture()

  def clearAllData: Future[Boolean] =
    collection.deleteMany(new BasicDBObject()).toFuture.map(_.wasAcknowledged())

  def getUniqueSlugNames: Future[Seq[String]] =
    collection.distinct("name").toFuture()

  def getSlugInfos(name: String, optVersion: Option[String]): Future[Seq[SlugInfo]] =
    optVersion match {
      case None          => collection.find(equal("name", name)).toFuture()
      case Some(version) => collection.find(and(equal("name", name), equal("version", version))).toFuture()
    }

  def getSlugInfo(name: String, flag: SlugInfoFlag): Future[Option[SlugInfo]] =
    collection
      .find(and(equal("name", name), equal(flag.asString, true)))
      .toFuture()
      .map(_.headOption)

  def getSlugsForEnv(flag: SlugInfoFlag): Future[Seq[SlugInfo]] =
    collection
      .find(equal(flag.asString, true))
      .toFuture()

  def clearFlag(flag: SlugInfoFlag, name: String): Future[Unit] = {
    logger.debug(s"clear ${flag.asString} flag on $name")

    collection
      .updateMany(filter = equal("name", name), update = set(flag.asString, false))
      .toFuture()
      .map(_ => ())
  }

  def markLatest(name: String, version: Version): Future[Unit] =
    setFlag(SlugInfoFlag.Latest, name, version)

  def setFlag(flag: SlugInfoFlag, name: String, version: Version): Future[Unit] =
    for {
      _ <- clearFlag(flag, name)
      _ = logger.debug(s"mark slug $name $version with ${flag.asString} flag")
      _ <- collection
        .updateOne(filter = and(equal("name", name), equal("version", version.original)), update = set(flag.asString, true))
        .toFuture()
    } yield ()

}
