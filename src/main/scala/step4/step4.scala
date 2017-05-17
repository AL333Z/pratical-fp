package step4

import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}
import scala.xml.Elem

trait Api[T[_], I, O] {
  def name: String

  def run(input: I): T[O]
}

trait ToXml[I] {
  def toXml(input: I): Elem
}

trait FromXml[O, E] {
  def fromXml(elem: Elem): Either[E, O]
}

trait Functor[F[_]] {
  def map[A, B](f: A => B)(fa: F[A]): F[B]
}

trait Applicative[F[_]] extends Functor[F] {
  def pure[A](a: A): F[A]
}

trait Monad[M[_]] extends Applicative[M] {
  def flatMap[A, B](f: A => M[B])(ma: M[A]): M[B]
}

trait MonadError[M[_], E] extends Monad[M] {
  def raiseError[A](e: E): M[A]
}

object step4 {

  case class GetOrderListInput(userId: String)

  case class Order(orderNo: String, userId: String)

  case class GetOrderListOutput(orderList: List[Order])

  implicit val getOrderListInputToXml = new ToXml[GetOrderListInput] {
    def toXml(input: GetOrderListInput): Elem = <Order UserId={input.userId}/>
  }

  implicit val getOrderListOutputFromXml = new FromXml[GetOrderListOutput, Throwable] {
    def fromXml(elem: Elem): Either[Throwable, GetOrderListOutput] = ???
  }

  implicit val tryMonadError = new MonadError[Try, Throwable] {

    override def map[A, B](f: (A) => B)(fa: Try[A]): Try[B] = fa match {
      case Success(a) => Success(f(a))
      case Failure(t) => Failure(t)
    }

    override def pure[A](a: A): Try[A] = Success(a)

    override def raiseError[A](e: Throwable): Try[A] = Failure(e)

    override def flatMap[A, B](f: (A) => Try[B])(ma: Try[A]): Try[B] = ma match {
      case Success(a) => f(a)
      case Failure(t) => Failure(t)
    }

  }

  implicit val futureMonadError = new MonadError[Future, Throwable] {
    def raiseError[A](e: Throwable): Future[A] = Future.failed(e)

    def flatMap[A, B](f: (A) => Future[B])(ma: Future[A]): Future[B] = ma.flatMap(f)

    def pure[A](a: A): Future[A] = Future.successful(a)

    def map[A, B](f: (A) => B)(fa: Future[A]): Future[B] = fa.map(f)
  }

}

case class ApiClient[T[_], E](invoker: ApiInvoker[T, E])(
  implicit M: MonadError[T, E]) {

  def getOrderList[I, O](implicit
                         toXml: ToXml[I],
                         fromXml: FromXml[O, E]): Api[T, I, O] =
    new Api[T, I, O] {
      override def name: String = "getOrderList"

      override def run(input: I): T[O] = {
        val xmlInput: Elem = toXml.toXml(input)
        val apiOutput: T[Elem] = invoker.invoke(xmlInput, name)
        M.flatMap { (x: Elem) =>
          fromXml.fromXml(x).fold(M.raiseError[O], M.pure[O])
        }(apiOutput)
      }
    }
}

trait ApiInvoker[T[_], E] {
  def invoke(input: Elem, apiName: String)(implicit M: MonadError[T, E]): T[Elem]
}

case class BlockingApiInvoker(host: String)
  extends ApiInvoker[Try, Throwable] {

  override def invoke(input: Elem, apiName: String)(
    implicit M: MonadError[Try, Throwable]): Try[Elem] = Try {
    // let's pretend this is implemented properly
    ???
  }
}

case class AsyncApiInvoker(host: String)
  extends ApiInvoker[Future, Throwable] {

  override def invoke(input: Elem, apiName: String)(
    implicit M: MonadError[Future, Throwable]): Future[Elem] = Future {
    // let's pretend this is implemented properly
    ???
  }
}

object Demo extends App {

  // import typeclass instances
  import step4.{getOrderListInputToXml, getOrderListOutputFromXml}
  import step4.futureMonadError

  // import models
  import step4.GetOrderListInput
  import step4.GetOrderListOutput

  val invoker = AsyncApiInvoker("foo")
  val apiClient = ApiClient[Future, Throwable](invoker)

  val input = GetOrderListInput("0001")
  val result: Future[GetOrderListOutput] = apiClient
    .getOrderList[GetOrderListInput, GetOrderListOutput]
    .run(input)
}
