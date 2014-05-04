package org.http4s
package cooldsl

import shapeless.{::, HList}
import scalaz.concurrent.Task
import org.http4s.cooldsl.BodyCodec.{BodyTransformer, Decoder}
import shapeless.ops.hlist.Prepend

import scala.language.existentials
import org.http4s.cooldsl.bits.HListToFunc

/**
 * Created by Bryce Anderson on 4/29/14.
 */

/** Provides the operations for generating a router
  *
  * @param method request methods to match
  * @param path path matching stack
  * @param validators header validation stack
  * @tparam T1 cumulative type of the required method for executing the router
  */
case class Router[T1 <: HList](method: Method,
                               private[cooldsl] val path: PathRule[_ <: HList],
                               validators: HeaderRule[_ <: HList])
                extends RouteExecutable[T1] with HeaderAppendable[T1] {

  override def >>>[T3 <: HList](v: HeaderRule[T3])(implicit prep1: Prepend[T3, T1]): Router[prep1.Out] =
    Router(method, path, And(validators,v))

  override def |>>[F](f: F)(implicit hf: HListToFunc[T1,Task[Response],F]): Goal = RouteExecutor.compile(this, f, hf)

  override def |>>>[F,O](f: F)(implicit hf: HListToFunc[T1,O,F]): CoolAction[T1, F, O] =
    new CoolAction(this, f, hf)

  def decoding[R](decoder: Decoder[R]): CodecRouter[T1,R] = CodecRouter(this, decoder)
}

case class CodecRouter[T1 <: HList, R](r: Router[T1], t: BodyTransformer[R])extends HeaderAppendable[T1] with RouteExecutable[R::T1] {

  override def >>>[T3 <: HList](v: HeaderRule[T3])(implicit prep1: Prepend[T3, T1]): CodecRouter[prep1.Out,R] =
    CodecRouter(r >>> v, t)

  override def |>>[F](f: F)(implicit hf: HListToFunc[R::T1,Task[Response],F]): Goal =
    RouteExecutor.compileWithBody(this, f, hf)

  override def |>>>[F,O](f: F)(implicit hf: HListToFunc[R::T1,O,F]): CoolAction[R::T1, F, O] =
    new CoolAction(this, f, hf)

  private[cooldsl] override def path: PathRule[_ <: HList] = r.path

  override def method: Method = r.method
}

private[cooldsl] trait RouteExecutable[T <: HList] {
  def method: Method
  def |>>[F](f: F)(implicit hf: HListToFunc[T,Task[Response],F]): Goal
  def |>>>[F, O](f: F)(implicit hf: HListToFunc[T,O,F]): CoolAction[T, F, O]
  private[cooldsl] def path: PathRule[_ <: HList]
}

private[cooldsl] trait HeaderAppendable[T1 <: HList] {
  def >>>[T2 <: HList](v: HeaderRule[T2])(implicit prep1: Prepend[T2, T1]): HeaderAppendable[prep1.Out]
}