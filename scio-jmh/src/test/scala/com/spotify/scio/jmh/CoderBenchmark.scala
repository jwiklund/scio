/*
 * Copyright 2019 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.scio.jmh

import java.io.{InputStream, OutputStream}
import java.util.concurrent.TimeUnit

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.spotify.scio.coders._
import com.spotify.scio.schemas._
import com.twitter.chill.IKryoRegistrar
import org.apache.beam.sdk.coders.{
  AtomicCoder,
  ByteArrayCoder,
  Coder => BCoder,
  SerializableCoder,
  StringUtf8Coder
}
import org.apache.beam.sdk.util.CoderUtils
import org.apache.beam.sdk.schemas.SchemaCoder
import org.apache.beam.sdk.values.TypeDescriptor
import org.openjdk.jmh.annotations._

final case class UserId(bytes: Array[Byte])
object UserId {
  implicit def coderUserId: Coder[UserId] = Coder.gen[UserId]
}
final case class User(id: UserId, username: String, email: String)
final case class SpecializedUser(id: UserId, username: String, email: String)
final case class SpecializedUserForDerived(id: UserId, username: String, email: String)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
class CoderBenchmark {
  // please don't use arrays outside of benchmarks
  val userId: UserId = UserId(Array[Byte](1, 2, 3, 4))

  // use standard coders
  val user: User = User(userId, "johndoe", "johndoe@spotify.com")

  // use hand-optimized coders
  val specializedUser: SpecializedUser =
    SpecializedUser(userId, "johndoe", "johndoe@spotify.com")
  val specializedUserForDerived: SpecializedUserForDerived =
    SpecializedUserForDerived(userId, "johndoe", "johndoe@spotify.com")

  val javaUser =
    new j.User(
      new j.UserId(Array[Byte](1, 2, 3, 4).map(x => x: java.lang.Byte)),
      "johndoe",
      "johndoe@spotify.com"
    )

  val tenTimes: List[SpecializedUserForDerived] = List.fill(10)(specializedUserForDerived)

  val kryoCoder = new KryoAtomicCoder[User](KryoOptions())
  val kryoJavaCoder = new KryoAtomicCoder[j.User](KryoOptions())
  val javaCoder: SerializableCoder[User] = SerializableCoder.of(classOf[User])
  val specializedCoder = new SpecializedCoder
  val specializedKryoCoder = new KryoAtomicCoder[SpecializedUser](KryoOptions())
  val derivedCoder: BCoder[SpecializedUserForDerived] =
    CoderMaterializer.beamWithDefault(Coder[SpecializedUserForDerived])
  val derivedListCoder: BCoder[List[SpecializedUserForDerived]] =
    CoderMaterializer.beamWithDefault(Coder[List[SpecializedUserForDerived]])

  val specializedMapKryoCoder = new KryoAtomicCoder[Map[String, Long]](KryoOptions())
  val derivedMapCoder: BCoder[Map[String, Long]] =
    CoderMaterializer.beamWithDefault(Coder[Map[String, Long]])
  val mapExample: Map[String, Long] = (1 to 1000).map(x => (s"stringvalue$x", x.toLong)).toMap

  val specializedStringListKryoCoder = new KryoAtomicCoder[List[String]](KryoOptions())
  val derivedStringListCoder: BCoder[List[String]] =
    CoderMaterializer.beamWithDefault(Coder[List[String]])
  val stringListExample: List[String] = (1 to 1000).map(x => s"stringvalue$x").toList

  val derivedTuple3Coder: BCoder[(Int, Int, Int)] =
    CoderMaterializer.beamWithDefault(Coder[(Int, Int, Int)])
  val tuple3Example: (Int, Int, Int) = (1, 10, 100)
  val derivedTuple4Coder: BCoder[(Int, Int, Int, Int)] =
    CoderMaterializer.beamWithDefault(Coder[(Int, Int, Int, Int)])
  val tuple4Example: (Int, Int, Int, Int) = (1, 10, 100, 1000)

  @Benchmark
  def tuple3Encode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(derivedTuple3Coder, tuple3Example)
    }

  @Benchmark
  def tuple4Encode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(derivedTuple4Coder, tuple4Example)
    }

  @Benchmark
  def kryoEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(kryoCoder, user)
    }

  @Benchmark
  def javaEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(javaCoder, user)
    }

  @Benchmark
  def customEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(specializedCoder, specializedUser)
    }

  @Benchmark
  def customKryoEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(specializedKryoCoder, specializedUser)
    }

  @Benchmark
  def derivedEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(derivedCoder, specializedUserForDerived)
    }

  @Benchmark
  def derivedListEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(derivedListCoder, tenTimes)
    }

  @Benchmark
  def kryoMapEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(specializedMapKryoCoder, mapExample)
    }

  @Benchmark
  def derivedMapEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(derivedMapCoder, mapExample)
    }

  @Benchmark
  def kryoStringListEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(specializedStringListKryoCoder, stringListExample)
    }

  @Benchmark
  def derivedStringListEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(derivedStringListCoder, stringListExample)
    }

  val kryoEncoded: Array[Byte] = kryoEncode(new SerializedOutputSize)
  val javaEncoded: Array[Byte] = javaEncode(new SerializedOutputSize)
  val customEncoded: Array[Byte] = customEncode(new SerializedOutputSize)
  val customKryoEncoded: Array[Byte] = customKryoEncode(new SerializedOutputSize)
  val derivedEncoded: Array[Byte] = derivedEncode(new SerializedOutputSize)
  val derivedListEncoded: Array[Byte] = derivedListEncode(new SerializedOutputSize)
  val kryoMapEncoded: Array[Byte] = kryoMapEncode(new SerializedOutputSize)
  val derivedMapEncoded: Array[Byte] = derivedMapEncode(new SerializedOutputSize)
  val kryoStringListEncoded: Array[Byte] = kryoStringListEncode(new SerializedOutputSize)
  val derivedStringListEncoded: Array[Byte] = derivedStringListEncode(new SerializedOutputSize)
  val tuple3Encoded: Array[Byte] = tuple3Encode(new SerializedOutputSize)
  val tuple4Encoded: Array[Byte] = tuple4Encode(new SerializedOutputSize)

  @Benchmark
  def tuple3Decode: (Int, Int, Int) =
    CoderUtils.decodeFromByteArray(derivedTuple3Coder, tuple3Encoded)

  @Benchmark
  def tuple4Decode: (Int, Int, Int, Int) =
    CoderUtils.decodeFromByteArray(derivedTuple4Coder, tuple4Encoded)

  @Benchmark
  def kryoDecode: User =
    CoderUtils.decodeFromByteArray(kryoCoder, kryoEncoded)

  @Benchmark
  def javaDecode: User =
    CoderUtils.decodeFromByteArray(javaCoder, javaEncoded)

  @Benchmark
  def customDecode: SpecializedUser =
    CoderUtils.decodeFromByteArray(specializedCoder, customEncoded)

  @Benchmark
  def customKryoDecode: SpecializedUser =
    CoderUtils.decodeFromByteArray(specializedKryoCoder, customKryoEncoded)

  @Benchmark
  def derivedDecode: SpecializedUserForDerived =
    CoderUtils.decodeFromByteArray(derivedCoder, derivedEncoded)

  @Benchmark
  def derivedListDecode: List[SpecializedUserForDerived] =
    CoderUtils.decodeFromByteArray(derivedListCoder, derivedListEncoded)

  @Benchmark
  def kryoMapDecode: Map[String, Long] =
    CoderUtils.decodeFromByteArray(specializedMapKryoCoder, kryoMapEncoded)

  @Benchmark
  def derivedMapDecode: Map[String, Long] =
    CoderUtils.decodeFromByteArray(derivedMapCoder, derivedMapEncoded)

  @Benchmark
  def kryoStringListDecode: Seq[String] =
    CoderUtils.decodeFromByteArray(specializedStringListKryoCoder, kryoStringListEncoded)

  @Benchmark
  def derivedStringListDecode: Seq[String] =
    CoderUtils.decodeFromByteArray(derivedStringListCoder, derivedStringListEncoded)

  // Compare the performance of Schema Coders vs compile time derived Coder. Run with:
  // jmh:run -f1 -wi 10 -i 20 com.spotify.scio.jmh.CoderBenchmark.(derived|schemaCoder)(De|En)code
  val (specializedUserSchema, specializedTo, specializedFrom) =
    SchemaMaterializer.materialize(
      Schema[SpecializedUserForDerived]
    )

  val specializedSchemaCoder: BCoder[SpecializedUserForDerived] =
    SchemaCoder.of(
      specializedUserSchema,
      TypeDescriptor.of(classOf[SpecializedUserForDerived]),
      specializedTo,
      specializedFrom
    )

  @Benchmark
  def schemaCoderEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(specializedSchemaCoder, specializedUserForDerived)
    }

  val shemaEncoded: Array[Byte] = schemaCoderEncode(new SerializedOutputSize)

  @Benchmark
  def schemaCoderDecode: SpecializedUserForDerived =
    CoderUtils.decodeFromByteArray(specializedSchemaCoder, shemaEncoded)

  // Compare the performance of Schema Coders vs Kryo coder for java class run with:
  // jmh:run -f1 -wi 10 -i 20 com.spotify.scio.jmh.CoderBenchmark.java(Kryo|Schema)CoderEncode
  val (javaUserSchema, javaTo, javaFrom) =
    SchemaMaterializer.materialize(
      Schema[j.User]
    )

  val javaSchemaCoder: BCoder[j.User] =
    SchemaCoder.of(javaUserSchema, TypeDescriptor.of(classOf[j.User]), javaTo, javaFrom)

  @Benchmark
  def javaSchemaCoderEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(javaSchemaCoder, javaUser)
    }

  val javaShemaEncoded: Array[Byte] = javaSchemaCoderEncode(new SerializedOutputSize)

  @Benchmark
  def javaSchemaCoderDecode: j.User =
    CoderUtils.decodeFromByteArray(javaSchemaCoder, javaShemaEncoded)

  @Benchmark
  def javaKryoCoderEncode(o: SerializedOutputSize): Array[Byte] =
    Counter.track(o) {
      CoderUtils.encodeToByteArray(kryoJavaCoder, javaUser)
    }

  val javaKryoEncoded: Array[Byte] = javaKryoCoderEncode(new SerializedOutputSize)

  @Benchmark
  def javaKryoCoderDecode: j.User =
    CoderUtils.decodeFromByteArray(kryoJavaCoder, javaKryoEncoded)
}

/** Counter to track the size of the serialized output */
@State(Scope.Thread)
@AuxCounters(AuxCounters.Type.EVENTS)
class SerializedOutputSize(var outputSize: Int) {
  def this() = { this(0) }
}

object Counter {
  def track[A](o: SerializedOutputSize)(f: => Array[Byte]): Array[Byte] = {
    val out = f
    if (o.outputSize == 0)
      o.outputSize = out.length
    out
  }
}

final class SpecializedCoder extends AtomicCoder[SpecializedUser] {
  def encode(value: SpecializedUser, os: OutputStream): Unit = {
    ByteArrayCoder.of().encode(value.id.bytes, os)
    StringUtf8Coder.of().encode(value.username, os)
    StringUtf8Coder.of().encode(value.email, os)
  }

  def decode(is: InputStream): SpecializedUser =
    SpecializedUser(
      UserId(ByteArrayCoder.of().decode(is)),
      StringUtf8Coder.of().decode(is),
      StringUtf8Coder.of().decode(is)
    )
}

final class SpecializedKryoSerializer extends Serializer[SpecializedUser] {
  def read(kryo: Kryo, input: Input, tpe: Class[SpecializedUser]): SpecializedUser = {
    val len = input.readInt()
    val array = new Array[Byte](len)

    input.readBytes(array)

    val username = input.readString()
    val email = input.readString()

    SpecializedUser(UserId(array), username, email)
  }

  def write(kryo: Kryo, output: Output, obj: SpecializedUser): Unit = {
    output.writeInt(obj.id.bytes.length)
    output.writeBytes(obj.id.bytes)
    output.writeString(obj.username)
    output.writeString(obj.email)
  }
}

@KryoRegistrar
class KryoRegistrar extends IKryoRegistrar {
  def apply(k: Kryo): Unit = {
    k.register(classOf[User])
    k.register(classOf[SpecializedUser], new SpecializedKryoSerializer)
    k.register(classOf[UserId])
    k.register(classOf[Array[Byte]])
    k.register(classOf[Array[java.lang.Byte]])
    k.register(classOf[j.UserId])
    k.register(classOf[j.User])

    k.setRegistrationRequired(true)
  }
}
