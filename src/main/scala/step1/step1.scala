package step1

import scala.language.higherKinds
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

object step1 {

  case class GetOrderListInput(userId: String)

  case class Order(orderNo: String, userId: String)

  case class GetOrderListOutput(orderList: List[Order])

  implicit val getOrderListInputToXml = new ToXml[GetOrderListInput] {
    def toXml(input: GetOrderListInput): Elem = <Order UserId={input.userId}/>
  }

  implicit val getOrderListOutputFromXml = new FromXml[GetOrderListOutput, Throwable] {
    def fromXml(elem: Elem): Either[Throwable, GetOrderListOutput] = ???
  }

}