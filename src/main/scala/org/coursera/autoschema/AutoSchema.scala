/**
  * Copyright 2014 Coursera Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package org.coursera.autoschema

import org.coursera.autoschema.annotations.Term.MultiSelect
import play.api.libs.json._

import scala.reflect.runtime.{universe => ru}

abstract class AutoSchema {
  _: TypeMappings =>

  private[this] val classSchemaCache = collection.concurrent.TrieMap[String, JsObject]()
  private[this] val isHideAnnotation = (annotation: ru.Annotation) => isOfType(annotation, "org.coursera.autoschema.annotations.Term.Hide")
  private[this] val isFormatAnnotation = (annotation: ru.Annotation) => isOfType(annotation, "org.coursera.autoschema.annotations.FormatAs")
  private[this] val isExposeAnnotation = (annotation: ru.Annotation) => isOfType(annotation, "org.coursera.autoschema.annotations.ExposeAs")
  private[this] val isTermExposeAnnotation = (annotation: ru.Annotation) => isOfType(annotation, "org.coursera.autoschema.annotations.Term.ExposeAs")
  private[this] val isDescriptionAnnotation = (annotation: ru.Annotation) => isOfType(annotation, "org.coursera.autoschema.annotations.Description")
  private[this] val isTitleAnnotation = (annotation: ru.Annotation) => isOfType(annotation, "org.coursera.autoschema.annotations.Title")
  private[this] val isOrderAnnotation = (annotation: ru.Annotation) => isOfType(annotation, "org.coursera.autoschema.annotations.Term.Order")
  private[this] val isMultiSelectAnnotation = (annotation: ru.Annotation) => isOfType(annotation, "org.coursera.autoschema.annotations.Term.MultiSelect")

  /**
    * Create schema based on reflection type
    *
    * @param tpe
    * The reflection type to be converted into JSON Schema
    * @return
    * The JSON Schema for the type as a JsObject
    */
  def createSchema(tpe: ru.Type): JsObject = createSchema(tpe, Set.empty)

  /**
    *
    * @tparam T
    * The type to be converted into JSON Schema
    * @return
    * The JSON Schema for the type as a JsObject
    */
  def createSchema[T: ru.TypeTag]: JsObject = createSchema(ru.typeOf[T])

  /**
    * Create a schema and format it according to the style
    *
    * @param tpe    The reflection type to be converted into JSON Schema
    * @param indent The left margin indent in pixels
    * @return The JSON Schema for the type as a formatted string
    */
  def createPrettySchema(tpe: ru.Type, indent: Int) =
    styleSchema(Json.prettyPrint(createSchema(tpe)), indent)

  /**
    * Create a schema and format it according to the style
    *
    * @param indent The left margin indent in pixels
    * @return The JSON Schema for the type as a formatted string
    */
  def createPrettySchema[T: ru.TypeTag](indent: Int) =
    styleSchema(Json.prettyPrint(createSchema(ru.typeOf[T])), indent)

  private def isOfType(annotation: ru.Annotation, tpe: String) = annotation.tree.tpe.typeSymbol.fullName == tpe

  // Generates JSON schema based on a FormatAs annotation
  private[this] def formatAnnotationJson(annotation: ru.Annotation) = {
    annotation.tree.children.tail match {
      case typ :: Nil =>
        Json.obj("type" -> typ.toString().tail.init)
      case typ :: format :: Nil =>
        Json.obj("type" -> typ.toString().tail.init, "format" -> format.toString().tail.init)
      case x =>
        Json.obj()
    }
  }

  private[this] def descriptionAnnotationJson(annotation: ru.Annotation) = {
    annotation.tree.children.tail match {
      case description :: Nil =>
        Some("description" -> JsString(description.toString().tail.init))
      case _ => None
    }
  }

  private[this] def titleAnnotationJson(annotation: ru.Annotation) = {
    annotation.tree.children.tail match {
      case title :: Nil =>
        Some("title" -> JsString(title.toString().tail.init))
      case _ => None
    }
  }

  private[this] def orderAnnotationInt(annotation: ru.Annotation) = {
    annotation.tree.children.tail match {
      case order :: Nil => Some(order.toString().toInt)
      case _ => None
    }
  }

  private[this] def multiSelectAnnotation(annotation: ru.Annotation) = {
    annotation.tree.children.tail match {
      case uniqueItems :: createIfNoneMatches :: Nil =>
        Some((uniqueItems.toString().toBoolean, createIfNoneMatches.toString().toBoolean))
      case _ => None
    }
  }

  private[this] def createClassJson(tpe: ru.Type, previousTypes: Set[String]) = {
    // Check if schema for this class has already been generated
    classSchemaCache.getOrElseUpdate(tpe.typeSymbol.fullName, {
      val title = tpe.typeSymbol.name.decodedName.toString

      val description : Option[(String, JsString)] = tpe.typeSymbol.annotations
                                                        .find(isDescriptionAnnotation)
                                                        .flatMap(descriptionAnnotationJson)

      var requiredValues = Seq[String]()
      val propertiesList = tpe.members.flatMap { member =>
        if (member.isTerm) {
          val term = member.asTerm
          if ((term.isVal || term.isVar) && !term.annotations.exists(isHideAnnotation)) {
            val termMultiSelect = term.annotations.find(isMultiSelectAnnotation).flatMap(multiSelectAnnotation)
            val termFormat = term.annotations.find(isFormatAnnotation)
              .map(formatAnnotationJson)
              .getOrElse {
                term.annotations.find(isTermExposeAnnotation)
                  .map(annotation =>
                    createSchema(annotation.tree.tpe.asInstanceOf[ru.TypeRefApi].args.head, previousTypes, termMultiSelect))
                  .getOrElse(createSchema(term.typeSignature, previousTypes + tpe.typeSymbol.fullName, termMultiSelect))
              }

            //If it is not an `Option`, it is required.
            if (term.typeSignature.typeSymbol.fullName != "scala.Option")
              requiredValues = requiredValues ++ Seq(term.name.decodedName.toString.trim)

            val description = term.annotations.find(isDescriptionAnnotation).flatMap(descriptionAnnotationJson)
            val termFormatWithDescription = description match {
              case Some(value) => termFormat + value
              case None => termFormat
            }

            val title = term.annotations.find(isTitleAnnotation).flatMap(titleAnnotationJson)
            val termFormatWithTitleAndDescription = title match {
              case Some(value) => termFormatWithDescription + value
              case None => termFormatWithDescription
            }

            val order = term.annotations.find(isOrderAnnotation).flatMap(orderAnnotationInt).getOrElse(0)

            Some(order -> (term.name.decodedName.toString.trim -> termFormatWithTitleAndDescription))
          } else {
            None
          }
        } else {
          None
        }
      }.toList.sortBy(_._1)

      val properties = JsObject(propertiesList.map(_._2))

      // Return the value and add it to the cache (since we're using getOrElseUpdate
      val representation = Json.obj(
        "title" -> title,
        "type" -> "object",
        "required" -> JsArray(requiredValues.map(JsString)),
        "properties" -> properties)

      description match {
        case Some(descr) => representation + descr
        case None => representation
      }
    })
  }

  private[this] def extendsValue(tpe: ru.Type) = {
    tpe.baseClasses.exists(_.fullName == "scala.Enumeration.Value")
  }

  private[this] def addDescription[T](tpe: ru.Type, obj: JsObject): JsObject = {
    val description = tpe.typeSymbol.annotations.find(isDescriptionAnnotation).flatMap(descriptionAnnotationJson)
    description match {
      case Some(descr) => obj + descr
      case None => obj
    }
  }

  private[this] def addTitle[T](tpe: ru.Type, obj: JsObject): JsObject = {
    val title = tpe.typeSymbol.annotations.find(isTitleAnnotation).flatMap(titleAnnotationJson)
    title match {
      case Some(ttl) => obj + ttl
      case None => obj
    }
  }

  private[this] def createSchema(tpe: ru.Type, previousTypes: Set[String], multiSelect: Option[(Boolean, Boolean)] = None): JsObject = {
    val typeName = tpe.typeSymbol.fullName

    if (extendsValue(tpe)) {
      val mirror = ru.runtimeMirror(getClass.getClassLoader)
      val enumName = tpe.toString.split('.').init.mkString(".")
      val module = mirror.staticModule(enumName)
      val enum = mirror.reflectModule(module).instance.asInstanceOf[Enumeration]
      val options = enum.values.map { v =>
        Json.toJson(v.toString)
      }.toList

      val optionsArr = JsArray(options)
      val enumJson = Json.obj(
        "type" -> "string",
        "enum" -> optionsArr
      )
      addDescription(tpe, enumJson)
      addTitle(tpe, enumJson)

    } else if (typeName == "scala.Option") {
      // Option[T] becomes the schema of T with required set to false
      val jsonOption = createSchema(tpe.asInstanceOf[ru.TypeRefApi].args.head, previousTypes)
      addDescription(tpe, jsonOption)
      addTitle(tpe, jsonOption)
    } else if (tpe.baseClasses.exists(s => s.fullName == "scala.collection.Traversable" ||
      s.fullName == "scala.Array" ||
      s.fullName == "scala.Seq" ||
      s.fullName == "scala.List" ||
      s.fullName == "scala.Vector")) {
      val jsonArrSeq = Json.obj("type" -> "array", "items" -> createSchema(tpe.asInstanceOf[ru.TypeRefApi].args.head, previousTypes, multiSelect))
      // (Traversable)[T] becomes a schema with items set to the schema of T
      val jsonSeq = multiSelect match {
          case Some((uniqueItems, createIfNoneMatches)) =>
            jsonArrSeq ++ Json.obj(
              "uniqueItems" -> JsBoolean(uniqueItems),
              "createIfNoneMatch" -> JsBoolean(createIfNoneMatches)
            )
          case None =>
            jsonArrSeq
        }
      addDescription(tpe, jsonSeq)
      addTitle(tpe, jsonSeq)
    } else if (multiSelect.isDefined) {
      // enum values should come from an external service (`/tags` for instance)
      val enumJson = Json.obj(
        "type" -> "string",
        "enum" -> JsArray()
      )
      addDescription(tpe, enumJson)
      addTitle(tpe, enumJson)
    } else {
      val jsonObj = tpe.typeSymbol.annotations.find(isFormatAnnotation)
        .map(formatAnnotationJson)
        .getOrElse {
          tpe.typeSymbol.annotations.find(isExposeAnnotation)
            .map(annotation => createSchema(annotation.tree.tpe.asInstanceOf[ru.TypeRefApi].args.head, previousTypes))
            .getOrElse {
              schemaTypeForScala(typeName).getOrElse {
                if (tpe.typeSymbol.isClass) {
                  // Check if this schema is recursive
                  if (previousTypes.contains(tpe.typeSymbol.fullName)) {
                    throw new IllegalArgumentException(s"Recursive types detected: $typeName")
                  }

                  createClassJson(tpe, previousTypes)
                } else {
                  Json.obj()
                }
              }
            }
        }
      addDescription(tpe, jsonObj)
      addTitle(tpe, jsonObj)
    }
  }

  private[this] def styleSchema(schema: String, indent: Int) =
    s"""<div style="margin-left: ${indent}px; background-color: #E8E8E8; border-width: 1px;"><i>$schema</i></div>"""
}

/**
  * AutoSchema lets you take any Scala type and create JSON Schema out of it
  *
  * @example
  * {{{
  *      // Pass the type as a type parameter
  *      case class MyType(...)
  *
  *      AutoSchema.createSchema[MyType]
  *
  *
  *      // Or pass the reflection type
  *      case class MyOtherType(...)
  *
  *      AutoSchema.createSchema(ru.typeOf[MyOtherType])
  * }}}
  */
object AutoSchema extends AutoSchema with DefaultTypeMappings
