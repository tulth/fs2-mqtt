/*
 * Copyright 2020 Frédéric Cabestre
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

package net.sigusr.mqtt.impl.protocol

import java.net.InetSocketAddress

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync}
import cats.implicits._
import enumeratum.values._
import fs2.io.tcp.SocketGroup
import fs2.{Pipe, Stream}
import net.sigusr.mqtt.impl.frames.Frame
import net.sigusr.mqtt.impl.protocol.Transport.Direction.{In, Out}
import scodec.Codec
import scodec.stream.{StreamDecoder, StreamEncoder}

import scala.concurrent.duration.FiniteDuration

sealed case class TransportConfig(
  host: String,
  port: Int,
  readTimeout: Option[FiniteDuration] = None,
  writeTimeout: Option[FiniteDuration] = None,
  numReadBytes: Int = 4096,
  traceMessages: Boolean = false
)

trait Transport[F[_]] {

  def inFrameStream: Stream[F, Frame]

  def outFrameStream: Pipe[F, Frame, Unit]

}

object Transport {

  sealed abstract class Direction(val value: Char, val color: String) extends CharEnumEntry
  object Direction extends CharEnum[Direction] {
    case object In extends Direction('←', Console.YELLOW)
    case object Out extends Direction('→', Console.GREEN)

    val values: IndexedSeq[Direction] = findValues
  }

  def apply[F[_]: Concurrent: ContextShift](
    transportConfig: TransportConfig
  ): Resource[F, Transport[F]] = for {
    blocker <- Blocker[F]
    socketGroup <- SocketGroup[F](blocker)
    socket <- socketGroup.client[F](new InetSocketAddress(transportConfig.host, transportConfig.port))
  } yield new Transport[F] {

    private def tracingPipe(d: Direction): Pipe[F, Frame, Frame] = frames => for {
      frame <- frames
      _ <- Stream.eval(Sync[F]
        .delay(println(s" ${d.value} ${d.color}$frame${Console.RESET}"))
        .whenA(transportConfig.traceMessages))
    } yield frame

    def outFrameStream: Pipe[F, Frame, Unit] = (frames: Stream[F, Frame]) =>
      frames
        .through(tracingPipe(Out))
        .through(StreamEncoder.many[Frame](Codec[Frame].asEncoder).toPipeByte)
        .through(socket.writes(transportConfig.writeTimeout))

    def inFrameStream: Stream[F, Frame] =
      socket.reads(transportConfig.numReadBytes, transportConfig.readTimeout)
        .through(StreamDecoder.many[Frame](Codec[Frame].asDecoder).toPipeByte)
        .through(tracingPipe(In))
  }
}