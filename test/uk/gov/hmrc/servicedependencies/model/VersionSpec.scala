/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.servicedependencies.model

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class VersionSpec extends AnyFreeSpec with Matchers {

  "Can be lower than others" in {
    Version("0.0.0") < Version("0.0.1") mustBe true
    Version("0.0.1") < Version("0.0.0") mustBe false

    Version("0.0.0") < Version("0.1.0") mustBe true
    Version("0.1.0") < Version("0.0.0") mustBe false

    Version("0.0.0") < Version("1.0.0") mustBe true
    Version("1.0.0") < Version("0.0.0") mustBe false

    Version("0.1.1") < Version("1.0.0") mustBe true
    Version("1.0.0") < Version("0.1.1") mustBe false

    Version("1.0.1") < Version("1.1.0") mustBe true
    Version("1.1.0") < Version("1.0.1") mustBe false

    Version("1.1.0") < Version("1.1.1") mustBe true
    Version("1.1.1") < Version("1.1.0") mustBe false

    Version("0.9.0-2-ga163db3") < Version("0.52.0") mustBe true
    Version("0.52.0") < Version("0.9.0-2-ga163db3") mustBe false

    Version("0.9.0-2-ga163db3") < Version("0.9.0") mustBe true
    Version("0.9.0") < Version("0.9.0-2-ga163db3") mustBe false

    Version("0.9.0-SNAPSHOT") < Version("0.9.0") mustBe true
    Version("0.9.0") < Version("0.9.0-SNAPSHOT") mustBe false
  }

  "Can be parsed from strings" in {
    Version("1.2.3")            mustBe Version(1, 2,  3, "1.2.3")
    Version("2.3.4-play-26")    mustBe Version(2, 3,  4, "2.3.4-play-26")
    Version("5.6.7-RC1")        mustBe Version(5, 6,  7, "5.6.7-RC1")
    Version("9.2.24.v20180105") mustBe Version(9, 2, 24, "9.2.24.v20180105")

    Version("2.5")              mustBe Version(2, 5, 0, "2.5")
    Version("2.19-SNAPSHOT")    mustBe Version(2, 19, 0, "2.19-SNAPSHOT")
    Version("2.2-cj-1.1")       mustBe Version(2, 2, 0, "2.2-cj-1.1")

    Version("2")                mustBe Version(0, 0, 2, "2")
    Version("999-SNAPSHOT")     mustBe Version(0, 0, 999, "999-SNAPSHOT")
  }

  "Can be printed to strings" in {
    Version(1, 2, 3                 ).toString mustBe "1.2.3"
    Version(1, 2, 3, "1.2.3-play-26").toString mustBe "1.2.3-play-26"
  }

  "apply.toString == identity" in {
    val testcases = List(
      "1.2.3",
      // with suffix
      "9.0.0-play-26",
      "1.0.7-alpha",
      "2.5.0-3",
      "2.0.0-M7",
      "1.7.0-akka-2.5.x",
      // with suffix but different suffix separator
      "9.2.24.v20180105",
      "3.9.9.Final",
      "2.1.0.1",
      "2.3.0_0.1.8",
      "3.5.5_a2.3",
      // other - unparseable
      "20080701",
      "r938",
      "999-SNAPSHOT",
      "2.5",
      "2.19-SNAPSHOT",
      "0.11-RC1",
      "2.5-20081211",
      "2.2-cj-1.1",
      "v2-rev137-1.23.0")
    testcases.foreach { s =>
      Version.apply(s).toString mustBe s
    }
  }
}
