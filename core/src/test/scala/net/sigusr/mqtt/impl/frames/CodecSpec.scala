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

package net.sigusr.mqtt.impl.frames

import net.sigusr.mqtt.SpecUtils._
import net.sigusr.mqtt.api._
import org.specs2.mutable._
import scodec.bits._
import scodec.{ Codec, Err }

import scala.util.Random

object CodecSpec extends Specification {

  "A remaining length codec" should {
    "Perform encoding of valid inputs" in {
      remainingLengthCodec.encode(0) should succeedWith(hex"00".bits)
      remainingLengthCodec.encode(127) should succeedWith(hex"7f".bits)
      remainingLengthCodec.encode(128) should succeedWith(hex"8001".bits)
      remainingLengthCodec.encode(16383) should succeedWith(hex"ff7f".bits)
      remainingLengthCodec.encode(16384) should succeedWith(hex"808001".bits)
      remainingLengthCodec.encode(2097151) should succeedWith(hex"ffff7f".bits)
      remainingLengthCodec.encode(2097152) should succeedWith(hex"80808001".bits)
      remainingLengthCodec.encode(268435455) should succeedWith(hex"ffffff7f".bits)
    }

    "Fail to encode certain input values" in {
      remainingLengthCodec.encode(-1) should failWith(Err(s"The remaining length must be in the range [0..268435455], -1 is not valid"))
      remainingLengthCodec.encode(268435455 + 1) should failWith(Err(s"The remaining length must be in the range [0..268435455], 268435456 is not valid"))
    }

    "Perform decoding of valid inputs" in {
      remainingLengthCodec.decode(hex"00".bits) should succeedWith((BitVector.empty, 0))
      remainingLengthCodec.decode(hex"7f".bits) should succeedWith((BitVector.empty, 127))
      remainingLengthCodec.decode(hex"8001".bits) should succeedWith((BitVector.empty, 128))
      remainingLengthCodec.decode(hex"ff7f".bits) should succeedWith((BitVector.empty, 16383))
      remainingLengthCodec.decode(hex"808001".bits) should succeedWith((BitVector.empty, 16384))
      remainingLengthCodec.decode(hex"ffff7f".bits) should succeedWith((BitVector.empty, 2097151))
      remainingLengthCodec.decode(hex"80808001".bits) should succeedWith((BitVector.empty, 2097152))
      remainingLengthCodec.decode(hex"ffffff7f".bits) should succeedWith((BitVector.empty, 268435455))
    }

    "Fail to decode certain input values" in {
      remainingLengthCodec.decode(hex"808080807f".bits) should failWith(Err("The remaining length must be 4 bytes long at most"))
    }
  }

  "A header codec" should {
    "Perform encoding of valid input" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = true)
      Codec.encode(header) should succeedWith(bin"0011")
    }

    "Perform decoding of valid inputs" in {
      val header = Header(dup = true, ExactlyOnce.enum, retain = false)
      Codec[Header].decode(bin"1100110011") should succeedWith((bin"110011", header))
    }
  }

  "A connect variable header codec" should {
    "Perform encoding of valid inputs" in {

      import net.sigusr.mqtt.impl.frames.ConnectVariableHeader._

      val connectVariableHeader = ConnectVariableHeader(cleanSession = true, willFlag = true, willQoS = AtMostOnce.enum, willRetain = false, passwordFlag = true, userNameFlag = true, keepAliveTimer = 1024)
      val res = connectVariableHeaderFixedBytes ++ bin"110001100000010000000000"
      Codec.encode(connectVariableHeader) should succeedWith(res)
    }

    "Perform decoding of valid inputs" in {

      import net.sigusr.mqtt.impl.frames.ConnectVariableHeader._

      val res = ConnectVariableHeader(cleanSession = false, willFlag = false, willQoS = AtLeastOnce.enum, willRetain = true, passwordFlag = false, userNameFlag = false, keepAliveTimer = 12683)
      Codec[ConnectVariableHeader].decode(connectVariableHeaderFixedBytes ++ bin"001010000011000110001011101010") should succeedWith((bin"101010", res))
    }
  }

  "A connect message codec should" should {
    "[0] Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connectVariableHeader = ConnectVariableHeader(userNameFlag = true, passwordFlag = true, willRetain = true, AtLeastOnce.enum, willFlag = true, cleanSession = true, 15)
      val connectMessage = ConnectFrame(header, connectVariableHeader, "clientId", Some("Topic"), Some("Message"), Some("User"), Some("Password"))

      val valid = Codec[Frame].encodeValid(connectMessage)
      Codec[Frame].decode(valid) should succeedWith((bin"", connectMessage))
    }

    "[1] Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connectVariableHeader = ConnectVariableHeader(userNameFlag = true, passwordFlag = false, willRetain = true, AtLeastOnce.enum, willFlag = false, cleanSession = true, 15)
      val connectMessage = ConnectFrame(header, connectVariableHeader, "clientId", None, None, Some("User"), None)

      Codec[Frame].decode(Codec[Frame].encodeValid(connectMessage)) should succeedWith((bin"", connectMessage))
    }

    "[2] Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connectVariableHeader = ConnectVariableHeader(userNameFlag = false, passwordFlag = false, willRetain = true, ExactlyOnce.enum, willFlag = false, cleanSession = false, 128)
      val connectMessage = ConnectFrame(header, connectVariableHeader, "clientId", None, None, None, None)

      Codec[Frame].decode(Codec[Frame].encodeValid(connectMessage)) should succeedWith((bin"", connectMessage))
    }

    "Perform encoding and match a captured value" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connectVariableHeader = ConnectVariableHeader(userNameFlag = false, passwordFlag = false, willRetain = true, AtLeastOnce.enum, willFlag = true, cleanSession = false, 60)
      val connectMessage = ConnectFrame(header, connectVariableHeader, "test", Some("test/topic"), Some("test death"), None, None)

      val capture = BitVector(0x10, 0x2a, 0x00, 0x06, 0x4d, 0x51, 0x49, 0x73, 0x64, 0x70, 0x03, 0x2c, 0x00, 0x3c, 0x00, 0x04, 0x74, 0x65, 0x73, 0x74, 0x00, 0x0a, 0x74, 0x65, 0x73, 0x74, 0x2f, 0x74, 0x6f, 0x70, 0x69, 0x63, 0x00, 0x0a, 0x74, 0x65, 0x73, 0x74, 0x20, 0x64, 0x65, 0x61, 0x74, 0x68)
      Codec[Frame].encode(connectMessage) should succeedWith(capture)
    }
  }

  "A connack message codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connackFrame = ConnackFrame(header, 0)

      Codec[Frame].decode(Codec[Frame].encodeValid(connackFrame)) should succeedWith((bin"", connackFrame))
    }

    "Perform decoding of captured values" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connackFrame = ConnackFrame(header, 0)

      Codec[Frame].decode(BitVector(0x20, 0x02, 0x00, 0x00)) should succeedWith((bin"", connackFrame))
    }
  }

  "A topics codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      import net.sigusr.mqtt.impl.frames.SubscribeFrame._
      val topics = Vector(("topic0", AtMostOnce.enum), ("topic1", AtLeastOnce.enum), ("topic2", ExactlyOnce.enum))
      Codec[Vector[(String, Int)]].decode(Codec[Vector[(String, Int)]].encodeValid(topics)) should succeedWith((bin"", topics))
    }
  }

  "A subscribe codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topics = Vector(("topic0", AtMostOnce.enum), ("topic1", AtLeastOnce.enum), ("topic2", ExactlyOnce.enum))
      val subscribeFrame = SubscribeFrame(header, 3, topics)
      Codec[Frame].decode(Codec[Frame].encodeValid(subscribeFrame)) should succeedWith((bin"", subscribeFrame))
    }

    "Perform encoding and match a captured value" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topics = Vector(("topic", AtLeastOnce.enum))
      val subscribeFrame = SubscribeFrame(header, 1, topics)
      val capture = BitVector(0x82, 0x0a, 0x00, 0x01, 0x00, 0x05, 0x74, 0x6f, 0x70, 0x69, 0x63, 0x01)
      Codec[Frame].encode(subscribeFrame) should succeedWith(capture)
    }
  }

  "A suback codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val qos = Vector(AtMostOnce.enum, AtLeastOnce.enum, ExactlyOnce.enum)
      val subackFrame = SubackFrame(header, 3, qos)
      Codec[Frame].decode(Codec[Frame].encodeValid(subackFrame)) should succeedWith((bin"", subackFrame))
    }
  }

  "An unsubscribe codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topics = Vector("topic0", "topic1")
      val unsubscribeFrame = UnsubscribeFrame(header, Random.nextInt(65536), topics)
      Codec[Frame].decode(Codec[Frame].encodeValid(unsubscribeFrame)) should succeedWith((bin"", unsubscribeFrame))
    }
  }

  "An unsuback codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val unsubackFrame = UnsubackFrame(header, Random.nextInt(65536))
      Codec[Frame].decode(Codec[Frame].encodeValid(unsubackFrame)) should succeedWith((bin"", unsubackFrame))
    }
  }

  "A disconnect message codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val disconnectFrame = DisconnectFrame(header)

      Codec[Frame].decode(Codec[Frame].encodeValid(disconnectFrame)) should succeedWith((bin"", disconnectFrame))
    }
  }

  "A ping request message codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val pingReqFrame = PingReqFrame(header)

      Codec[Frame].decode(Codec[Frame].encodeValid(pingReqFrame)) should succeedWith((bin"", pingReqFrame))
    }

    "Perform encoding and match a captured value" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val connectMessage = PingReqFrame(header)

      val capture = BitVector(0xc0, 0x00)
      Codec[Frame].encode(connectMessage) should succeedWith(capture)
    }
  }

  "A ping response message codec" should {
    "Perform round trip encoding/decoding of a valid input" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val pingRespFrame = PingRespFrame(header)

      Codec[Frame].decode(Codec[Frame].encodeValid(pingRespFrame)) should succeedWith((bin"", pingRespFrame))
    }

    "Perform decoding of captured values" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val pingRespFrame = PingRespFrame(header)

      Codec[Frame].decode(BitVector(0xd0, 0x00)) should succeedWith((bin"", pingRespFrame))
    }
  }

  "A publish message codec" should {
    "Perform round trip encoding/decoding of a valid input with a QoS greater than 0" in {
      val header = Header(dup = false, AtLeastOnce.enum, retain = false)
      val topic = "a/b"
      val publishFrame = PublishFrame(header, topic, 10, ByteVector("Hello world".getBytes))

      Codec[Frame].decode(Codec[Frame].encodeValid(publishFrame)) should succeedWith((bin"", publishFrame))
    }

    "Perform round trip encoding/decoding of a valid input with a QoS equals to 0" in {
      val header = Header(dup = false, AtMostOnce.enum, retain = false)
      val topic = "a/b"
      val publishFrame = PublishFrame(header, topic, 0, ByteVector("Hello world".getBytes))

      Codec[Frame].decode(Codec[Frame].encodeValid(publishFrame)) should succeedWith((bin"", publishFrame))
    }
  }
}