package step2

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

class ApiClient[T[_], E] {

}

object step2 {

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

}
