/**
 *  Copyright 2014 Coursera Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.coursera.autoschema.annotations

import org.coursera.autoschema.annotations

import scala.annotation.StaticAnnotation
import scala.annotation.meta.field

/**
 * Lets you manually set the type and format to be used when generating schema for the annotated type
 * @example
 * {{{
 *      @FormatAs("string", "date")
 *      case class MyDateType
 * }}}
 *
 * @param tpe
 * The type to be used when generating the schema
 * @param format
 * Optional: How the type should be viewed
 */
class FormatAs(tpe: String, format: String) extends StaticAnnotation {
  /**
   *
   * @param tpe
   * The type to be used when generating the schema
   */
  def this(tpe: String) = this(tpe, "")
}

/**
 * Lets you set the schema for one type to be the schema of another type
 * @example
 * {{{
 *      @ExposeAs[Int]
 *      case class MyIntLikeType
 * }}}
 * @tparam T
 * The type whose schema will be used
 * @note
 * Ignored if FormatAs is present
 */
class ExposeAs[T] extends StaticAnnotation

/**
 * Lets you manually set a description for a field
 * @example
 * {{{
 *      @Description("This is a custom date")
 *      case class MyDateType
 * }}}
 * @param description the class description
 *
 * Optional: A description of the annotated field
 */
class Description(description: String) extends StaticAnnotation

/**
  * Lets you manually set a title to override the default
  * @example
  * {{{
  *      @Title("Title")
  *      case class MyDateType
  * }}}
  *
  * @param title the class title
  */
class Title(title: String) extends StaticAnnotation

/**
 * Contains AutoSchema annotations that can be used on fields (vals, vars, etc.)
 */
// @field marks annotations as usable on fields
object Term {
  /**
   * Same as [[annotations.FormatAs]] but for fields
   * @example
   * {{{
   *      case class MyType(@Term.FormatAs("string", "date") date: String)
   * }}}
   */
  type FormatAs = annotations.FormatAs@field

  /**
   * Same as [[annotations.ExposeAs]] but for fields
   * @example
   * {{{
   *      case class MyType(@Term.ExposeAs[Id] id: MyTypeId)
   * }}}
   * @tparam T
   * The type whose schema will be used
   */
  // Have to do this since annotation takes type parameters
  @field
  class ExposeAs[T] extends annotations.ExposeAs[T]

  /**
   * Hides the annotated field in the generated schema
   * @example
   * {{{
   *      case class MyType(@Term.Hide mySecretField: Int)
   * }}}
   */
  @field
  class Hide extends StaticAnnotation

  /**
   * Same as [[annotations.Description]] but for fields
   * @example
   * {{{
   *      case class MyType(@Term.Description("some id") id: MyTypeId)
   * }}}
   */
  @field
  type Description = annotations.Description@field

  /**
    * Same as [[annotations.Title]] but for fields
    *
    * @example
    * {{{
    *      case class MyType(@Term.Title("My custom title") mySecretField: Int)
    * }}}
    */
  @field
  type Title = annotations.Title@field

  /**
    * Orders the annotated field in the generated schema.
    * It deals with a relative order compare to other fields. A field with a lower order will be up in the json schema.
    * A field with a higher order will be down.
    *
    * @param order the relative order of the field
    * @example
    * {{{
    *      case class MyType(@Term.Order(2) myField1: Int, @Term.Order(1) myField2: Int)
    * }}}
    */
  @field
  class Order(order: Int) extends StaticAnnotation

  /**
    * Transform the annotated field into a json schema compatible with the use of multiselect tag input field
    *
    * @example
    * {{{
    *      case class MyType(@Term.MultiSelect myField1: Seq[String])
    * }}}
    */
  @field
  class MultiSelect(val uniqueItems: Boolean = true, val createIfNoneMatches: Boolean = true) extends StaticAnnotation


}
