package zio.http.endpoint.cli

import zio._
import zio.cli._
import zio.test.Gen

import zio.schema.Schema

import zio.http._
import zio.http.codec._
import zio.http.endpoint.cli.AuxGen._
import zio.http.endpoint.cli.CliRepr._
import zio.http.internal.StringSchemaCodec.PrimitiveCodec

/**
 * Constructs a Gen[Options[CliRequest], CliEndpoint]
 */

object OptionsGen {

  def toPathCodec[A](name: String, textCodec: TextCodec[A]): PathCodec[A] =
    textCodec match {
      case TextCodec.UUIDCodec    => PathCodec.uuid(name)
      case TextCodec.StringCodec  => PathCodec.string(name)
      case TextCodec.IntCodec     => PathCodec.int(name)
      case TextCodec.LongCodec    => PathCodec.long(name)
      case TextCodec.BooleanCodec => PathCodec.bool(name)
      case TextCodec.Constant(_)  => PathCodec.empty
    }

  def encodeOptions[A](name: String, textCodec: TextCodec[A]): Options[String] =
    HttpOptions
      .optionsFromTextCodec(textCodec)(name)
      .map(value => textCodec.encode(value))

  def encodeOptions[A](name: String, codec: PrimitiveCodec[A], schema: Schema[A]): Options[String] =
    HttpOptions
      .optionsFromSchema(schema)(name)
      .map(value => codec.encode(value))

  lazy val anyBodyOption: Gen[Any, CliReprOf[Options[Retriever]]] =
    Gen
      .alphaNumericStringBounded(1, 30)
      .zip(anyMediaType)
      .zip(anySchema)
      .map {
        case (name, mediaType, schema) => {
          val body = HttpOptions.Body(name, mediaType, schema)
          CliRepr(body.options, CliEndpoint(body = body :: Nil))
        }
      }

  lazy val anyHeaderOption: Gen[Any, CliReprOf[Options[Headers]]] =
    Gen.alphaNumericStringBounded(1, 30).zip(anyTextCodec).map { case (name, codec) =>
      CliRepr(
        encodeOptions(name, codec)
          .map(value => Headers(name, value)),
        CliEndpoint(headers = HttpOptions.Header(name, codec) :: Nil),
      )
    }

  lazy val anyURLOption: Gen[Any, CliReprOf[Options[String]]] =
    Gen.oneOf(
      Gen
        .alphaNumericStringBounded(1, 30)
        .zip(anyTextCodec)
        .map {
          case (_, TextCodec.Constant(value)) =>
            CliRepr(
              Options.Empty.map(_ => value),
              CliEndpoint(url = HttpOptions.Path(PathCodec.literal(value)) :: Nil),
            )
          case (name, codec)                  =>
            CliRepr(
              encodeOptions(name, codec),
              CliEndpoint(url = HttpOptions.Path(toPathCodec(name, codec)) :: Nil),
            )
        },
      Gen
        .alphaNumericStringBounded(1, 30)
        .zip(anyStandardType)
        .map { case (name, schema) =>
          val codec = QueryCodec.query(name)(schema).asInstanceOf[HttpCodec.Query[Any]]
          CliRepr(
            encodeOptions(name, codec.codec.recordFields.head._2, schema.asInstanceOf[Schema[Any]]),
            CliEndpoint(url = HttpOptions.Query(codec.codec.recordFields.head._2, name) :: Nil),
          )
        },
    )

  lazy val anyMethod: Gen[Any, CliReprOf[Method]] =
    Gen
      .fromIterable(List(Method.GET, Method.DELETE, Method.POST, Method.PUT))
      .map(method => CliRepr(method, CliEndpoint(methods = method)))

  lazy val anyCliEndpoint: Gen[Any, CliReprOf[Options[CliRequest]]] =
    Gen
      .listOf(anyBodyOption)
      .zip(Gen.listOf(anyHeaderOption))
      .zip(Gen.listOf(anyURLOption))
      .zip(anyMethod)
      .map { case (body, header, url, method) =>
        CliRepr(
          (url
            .map(_.value)
            .foldLeft(Options.Empty.map(_ => Path.empty)) { case (path, str) =>
              (path ++ str).map { case (path, str) =>
                path / str
              }
            }
            ++ header
              .map(_.value)
              .foldLeft(Options.Empty.map(_ => Headers.empty)) { case (headers, header) =>
                (headers ++ header).map { case (headers, header) =>
                  headers ++ header
                }
              }
            ++ body
              .map(_.value)
              .foldLeft(Options.Empty.map(_ => Chunk.empty[Retriever])) { case (chunk, retriever) =>
                (chunk ++ retriever).map { case (chunk, retriever) =>
                  chunk ++ Chunk(retriever)
                }
              }).map { case (url, header, body) =>
            CliRequest(body, header, method.value, URL(url))
          }, // body.map(_.repr)  ++++ header.map(_.repr) ++ url.map(_.repr)        CliRequest(body, header, method.value, URL(url))
          (body.map(_.repr) ++ header.map(_.repr) ++ url.map(_.repr) ++ List(method.repr)).foldLeft(CliEndpoint.empty) {
            case (cli1, cli2) => cli1 ++ cli2
          },
        )
      }

}
