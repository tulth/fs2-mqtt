/*
 * Copyright 2014 Frédéric Cabestre
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

import messages._
import org.specs2.mutable._

class MessagesSpec extends Specification {

  "Message types" should {
    "Provide their «enum» value" in {
      CONNECT.enum should be_==(1)
      SUBACK.enum should be_==(9)
      DISCONNECT.enum should be_==(14)
    }

    "Be constructable from their corresponding «enum» value" in {
      import messages.MessageTypes._
      import SpecUtils._

      fromEnum(0) should failWith("Message type encoded value should be in the range [1..14]")
      fromEnum(2) should succeedWith(CONNACK)
      fromEnum(7) should succeedWith(PUBCOMP)
      fromEnum(11) should succeedWith(UNSUBACK)
      fromEnum(15) should failWith("Message type encoded value should be in the range [1..14]")
    }
  }

  "Quality of service" should {
    "Provide their «enum» value" in {
      AtMostOnce.enum should be_==(0)
      AtLeastOnce.enum should be_==(1)
      ExactlyOnce.enum should be_==(2)
    }

    "Be constructable from their corresponding «enum» value" in {
      import messages.QualityOfService._
      import SpecUtils._

      fromEnum(-1) should failWith("Quality of service encoded value should be in the range [0..2]")
      fromEnum(0) should succeedWith(AtMostOnce)
      fromEnum(1) should succeedWith(AtLeastOnce)
      fromEnum(2) should succeedWith(ExactlyOnce)
      fromEnum(3) should failWith("Quality of service encoded value should be in the range [0..2]")
    }
  }
}
