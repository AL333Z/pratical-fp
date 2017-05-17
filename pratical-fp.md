Practical Functional Programming, from first principles
===

---

Motivation
===

Because we don't like
- mutable state, variables
- hidden side-effects
- runtime exceptions
- weakly typed languages

---

A use case
===

Let's say we want to model an **api client**.

Requirements:
- The client should be **flexible** and **generic**:
  - the end-user should be able to decide if each call has to be **blocking, async**, etc..
  - the end-user should be able to decide in which domain (**ADT**) each response has to be decoded
  - the request/response body needs to respect a contract, with a defined content type (e.g. plain xml, json body with headers, etc..)
  - assumption for this session: requests/responses will only contain nothing more than plain xml in their bodies
- The client must handle errors, explicitly
- All side-effects must be composed explicitly

---

A use case, pt. 2
===

Let's say we have a sample `getOrderList` call, with the following structure:

  - input
```xml
<Order UserId="00001" />
```

  - output
```xml
<OrderList>
<Order OrderNo="0123456789" UserId="000001">...<Order/>
<Order OrderNo="0987654321" UserId="000001">...<Order/>
<OrderList/>
```

---

TDD
===

Just joking.

---

Type Driven Development
===

Start with a bunch of adts and structures, introduce some more, refactor.
Testing is still important, but the design is driven by types.

---

Step 1 - Foundations
===

```scala
trait Api[T[_], I, O] {
  def name: String
  def run(input: I): T[O]
}
```

`T` is a **higher-kinded** type. 
It's like data constructor, but for types.

`T[_]` means that the type `T` has a "hole", so it'll need to receive another type to be concrete.

In this intial formulation, everything is generic.
The input `I`, the output `O` and also the container (or context) `T`.

---

Step 1, pt. 2 - FromXml, ToXml
===
```scala
trait ToXml[I] {
  def toXml(input: I): Elem
}

trait FromXml[O, E] {
  def fromXml(elem: Elem): Either[E, O]
}
```

Let's introduce two typeclasses:
- `ToXml` will let the user specify an input `I` for the api
- `FromXml` will take care of decoding api output, returing a parsed result `O` or an error `E`

Everything is very abstract and generic. Another way to read these:
- for any `I` you pass as input, there'll be an instance of `ToXml[I]` which will know how to serialize to xml
- same for `FromXml[O]`

---

Ad-hoc polymorphism
===

A way to add behaviors without altering ADT structure.
Implemented throught **typeclasses**.

- ADT
```scala
case class GetOrderListInput(userId: String)
```
- Typeclass
```scala 
trait ToXml[I] {
  def toXml(input: I): Elem
}
```
 - Typeclass instance for the ADT
```scala 
implicit val getOrderListInputToXml = 
  new ToXml[GetOrderListInput] {
    def toXml(input: GetOrderListInput): Elem = 
      <Order UserId={input.userId}/>
  }
```
---

Ad-hoc polymorphism pt. 2
===

The pattern is simple:
0. define the ADT
1. define a typeclass, adding some behavior
2. define an instance, which will enable the new behavior for the ADT
3. import the instance in scope, and start profit.

---
Step 1, pt. 3 - FromXml, ToXml instances
===

```scala
  // 0
  case class GetOrderListInput(userId: String)

  case class Order(orderNo: String, userId: String)
  case class GetOrderListOutput(orderList: List[Order])

  // 1
  trait ToXml[I] { def toXml(input: I): Elem }
  trait FromXml[O, E] { def fromXml(elem: Elem): Either[E, O] }

  // 2
  implicit val getOrderListInputToXml = 
    new ToXml[GetOrderListInput] {
      def toXml(input: GetOrderListInput): Elem = 
        <Order UserId={input.userId}/>
    }

  implicit val getOrderListOutputFromXml = 
    new FromXml[GetOrderListOutput, Throwable] {
      def fromXml(elem: Elem): Either[Throwable, GetOrderListOutput] = 
        ??? // not relevant here...
    }
```
---

Step 1 - Outline
===

What we defined:
- An abstraction to model apis
- Adt, typeclasses and instances to serialize/deserialize arbitrary input to/from xml

Concepts:
- Higher-kinds: `T[_]`
- Immutable modelling with ADT
- Ad-hoc polymorphism and typeclass
- Explicit error types: `Either`

---

Step 2 - Structures pt.1
===

Now that we defined some basic ADTs and typeclasses, let's move forward toward **compositionability**..

Let's start redefining some basic typeclass (as an exercise..)

- **Functor**, a context with something that is *map*-able
```scala
trait Functor[F[_]] {
  def map[A, B](f: A => B)(fa: F[A]): F[B]
}
```
- **Applicative**, a Functor with the ability of embedding *pure* expressions in the context
```scala
trait Applicative[F[_]] extends Functor[F] {
  def pure[A](a: A): F[A]
}
```

---
Step 2 - Structures pt.2
===
- **Monad**, an Applicative with also operations that don't preserve the structure.
```scala
trait Monad[M[_]] extends Applicative[M] {
  def flatMap[A, B](f: A => M[B])(ma: M[A]): M[B]
}
```

- **MonadError**, a Monad that also manage error handling.

```scala
trait MonadError[M[_], E] extends Monad[M] {
  def raiseError[A](e: E): M[A]
}
```
---
Step 2 - Structures pt.3
===

Let's define an `MonadError` instance for `Try`.

```scala
trait Try[+A]
sealed case class Success[A](value: A) extends Try[A]
sealed case class Failure[A](err: Throwable) extends Try[Nothing]
```

Since a `MonadError` is a `Monad`, and a `Monad` is an `Applicative`, and an `Applicative` is a `Functor`..

```scala
  implicit val tryMonadError = new MonadError[Try, Throwable] {
    def map[A, B](f: (A) => B)(fa: Try[A]): Try[B] = fa match {
      case Success(a) => Success(f(a))
      case Failure(t) => Failure(t)
    }
    def pure[A](a: A): Try[A] = Success(a)
    def raiseError[A](e: Throwable): Try[A] = Failure(e)
    def flatMap[A, B](f: (A) => Try[B])(ma: Try[A]): Try[B] = 
      ma match {
        case Success(a) => f(a)
        case Failure(t) => Failure(t)
      }
  }
```

---

Step 2 - Outline
===

Concepts:
- FP basic structures: Functor, Applicative, Monad
- Deriving an instance for an existing ADT: `Try` as a `MonadError`

NB:
- Redefining structures and instances is a great exercise. But usually you'll just import a lib (scalaz, catz, etc..)
- In this session I'm not talking about laws which each structure need to respect

---

Step 3 - Do the job
===

Let's start by defining a generic `ApiClient`.

```scala
class ApiClient[T[_], E](implicit M: MonadError[T, E]) {}
```
The purpose of this class is to fix `T` and `E` to be a `MonadError`:
- `T` will be the **context** in which the computation will run
- `E` will be the **error** type is something goes wrong

The choice of `T` and `E` will tell us a lot:
- `T` may be a context which support blocking or async calls. Leaving this `T` generic at this stage will enable us to inject different implementation later (e.g. specifying `Future`, or `Task`, or `Try`, or `Either` and so on..)
- `E` will be the error type. Depending on which granularity we want to model errors, we can use `Throwable`, or some custom ADT of errors.
---
Step 3 - ApiClient
===

```scala
class ApiClient[T[_], E](implicit M: MonadError[T, E]) {

  def getOrderList[I, O](implicit 
    toXml: ToXml[I], 
    fromXml: FromXml[O, E]): Api[T, I, O] = 
      new Api[T, I, O] {
        override def name: String = "getOrderList"
        override def run(input: I): T[O] = ???
      }
}
```
Getting closer, but we're still missing the actual "api invocation" part.

---
Step 3 - Api invocation
===

Let's define an `ApiInvoker` which will run the actual call (http, in-process, ..)

```scala
trait ApiInvoker[T[_], E] {
  def invoke(input: Elem, apiName: String)(
    implicit M: MonadError[T, E]): T[Elem]
}
```

And let's inject that in the `ApiClient`

```scala
case class ApiClient[T[_], E](invoker: ApiInvoker[T, E])(
  implicit M: MonadError[T, E]) {
  ...
}
```

---
Step 3 - Composing abstractions
===

```scala
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
          val fromXmlEither: Either[E, O] = fromXml.fromXml(x)
          fromXmlEither.fold(
            error => M.raiseError[O](error),
            actualOutput => M.pure[O](actualOutput)
          )
        }(apiOutput)
      }
    }
}
```
---

---
Step 3 - A simple Invoker with Try
===

Let's say that our context should be blocking, and we wonna use `Try` for simplicity.

```scala
case class BlockingApiInvoker(host: String)
  extends ApiInvoker[Try, Throwable] {

  override def invoke(input: Elem, apiName: String)(
    implicit M: MonadError[Try, Throwable]): Try[Elem] = Try {
    // let's pretend this is calling something and 
    // returning a proper Elem, or throw
    ???
  }
}
```

NB: Remember that we already defined an instance for `MonadError[Try, Throwable]` previously.

---

Step 3 - Actual end-user code, finally
===

```scala
object Demo extends App {

  // import typeclass instances
  import step3.{getOrderListInputToXml, getOrderListOutputFromXml}
  import step3.tryMonadError

  // import models
  import step3.GetOrderListInput
  import step3.GetOrderListOutput

  val invoker = BlockingApiInvoker("foo")
  val apiClient = ApiClient[Try, Throwable](invoker)

  val input = GetOrderListInput("0001")
  val result: Try[GetOrderListOutput] = 
    apiClient
      .getOrderList[GetOrderListInput, GetOrderListOutput]
      .run(input)
}
```
---

Step 3 - Outline
===

- Reasoning at a higher-level
- Full typesafety
- No runtime exceptions
- All effects tracked down in types
- All effects depend only on low-level mechanism (invoker)
- Simple end-user API

---

Step 4 - An async api client
===

As simple as:
1. Choose the right async context. In this session: plain scala's `Future`.
1. Provide an instance for `MonadError[Future, Throwable]
1. Define an invoker wich will handle api call returning `Future`s.

---
Step 4 - Provide an instance for `MonadError[Future, E]`
===

No big deal here, only adapting Future methods to MonadError.

```scala
  implicit val futureMonadError = 
    new MonadError[Future, Throwable] {
   
    def map[A, B](f: (A) => B)(fa: Future[A]): Future[B] = 
      fa.map(f)
      
    def flatMap[A, B](f: (A) => Future[B])(ma: Future[A]): Future[B] = 
      ma.flatMap(f)

    def pure[A](a: A): Future[A] = 
      Future.successful(a)

    def raiseError[A](e: Throwable): Future[A] = 
      Future.failed(e)
  }
```
---
Define an invoker wich will handle api call returning `Future`s.
===
```scala
case class AsyncApiInvoker(host: String)
  extends ApiInvoker[Future, Throwable] {

  override def invoke(input: Elem, apiName: String)(
    implicit M: MonadError[Future, Throwable]): Future[Elem] = Future {
    // let's pretend this is implemented properly
    ???
  }
}
```
---
Step 4 - Demo
===

```scala
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
```
---
Step 4 - Outline
===

- Ad-hoc polymorphism in action
- Different types, different effects, same architecture.
