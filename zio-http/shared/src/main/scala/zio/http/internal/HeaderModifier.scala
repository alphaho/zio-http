/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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

package zio.http.internal

import zio.Trace

import zio.schema.Schema

import zio.http.Header.HeaderType
import zio.http._

/**
 * Maintains a list of operators that modify the current Headers. Once modified,
 * a new instance of the same type is returned. So or eg:
 * `request.addHeader("A", "B")` should return a new `Request` and similarly
 * `headers.add("A", "B")` should return a new `Headers` instance.
 *
 * NOTE: Add methods here that modify the current headers and returns an
 * instance of the same type.
 */
trait HeaderModifier[+A] { self =>
  final def addHeader(header: Header): A =
    addHeaders(Headers(header))

  protected def addHeader(name: CharSequence, value: CharSequence): A =
    addHeaders(Headers.apply(name, value))

  final def addHeaders(headers: Headers): A = updateHeaders(_ ++ headers)

  final def addHeaders(headers: Iterable[(CharSequence, CharSequence)]): A =
    addHeaders(Headers.fromIterable(headers.map { case (k, v) => Header.Custom(k, v) }))

  /**
   * Adds a header / headers with the specified name and based on the given
   * value. The value type must have a schema and can be a primitive type (e.g.
   * Int, String, UUID, Instant etc.), a case class with a single field or a
   * collection of either of these.
   */
  final def addHeader[T](name: String, value: T)(implicit schema: Schema[T]): A =
    updateHeaders(StringSchemaCodec.headerFromSchema(schema, ErrorConstructor.header, name).encode(value, _))

  /**
   * Adds headers based on the given value. The type of the value must have a
   * schema and be a case class and all fields will be added as headers. So
   * fields must be of primitive types (e.g. Int, String, UUID, Instant etc.), a
   * case class with a single field or a collection of either of these. The
   * header names are the field names.
   */
  final def addHeader[T](value: T)(implicit schema: Schema[T]): A =
    updateHeaders(StringSchemaCodec.headerFromSchema(schema, ErrorConstructor.header, null).encode(value, _))

  final def removeHeader(headerType: HeaderType): A = removeHeader(headerType.name)

  final def removeHeader(name: String): A = removeHeaders(Set(name))

  final def removeHeaders(headers: Set[String]): A =
    updateHeaders(orig => Headers(orig.filterNot(h => headers.exists(h.headerName.equalsIgnoreCase))))

  final def setHeaders(headers: Headers): A = self.updateHeaders(_ => headers)

  /**
   * Updates the current Headers with new one, using the provided update
   * function passed.
   */
  def updateHeaders(update: Headers => Headers)(implicit trace: Trace): A
}
