package bsp.codegen

import bsp.codegen.Def._
import bsp.codegen.Primitive._
import bsp.codegen.Type._
import cats.syntax.all._
import software.amazon.smithy.model.shapes.ShapeId

import dsl._
import bsp.codegen.EnumType.IntEnum
import bsp.codegen.EnumType.StringEnum

class Renderer(basepkg: String) {

  val baseRelPath = os.rel / basepkg.split('.')
  // scalafmt: { maxColumn = 120}
  def render(definition: Def): Option[CodegenFile] = {
    definition match {
      case Structure(shapeId, fields)            => Some(renderStructure(shapeId, fields))
      case ClosedEnum(shapeId, enumType, values) => Some(renderClosedEnum(shapeId, enumType, values))
      case OpenEnum(shapeId, enumType, values)   => None
      case Service(shapeId, operations)          => None
    }
  }

  def renderStructure(shapeId: ShapeId, fields: List[Field]): CodegenFile = {
    val allLines = lines(
      renderPkg(shapeId),
      newline,
      "import org.eclipse.lsp4j.jsonrpc.validation.NonNull",
      "import org.eclipse.lsp4j.generator.JsonRpcData",
      renderImports(fields),
      newline,
      "@JsonRpcData",
      block(s"class ${shapeId.getName()}")(
        lines(fields.map(renderJavaField)),
        newline, {
          val params = fields.map(renderParam).mkString(", ")
          val assignments = fields.map(_.name).map(n => s"this.$n = $n")
          block(s"new($params)")(assignments)
        }
      )
    )

    val fileName = shapeId.getName() + ".xtends"
    CodegenFile(baseRelPath / fileName, allLines.render)
  }

  def renderClosedEnum[A](shapeId: ShapeId, enumType: EnumType[A], values: List[EnumValue[A]]): CodegenFile = {
    val evt = enumValueType(enumType)
    val tpe = shapeId.getName()
    val allLines = lines(
      renderPkg(shapeId).map(_ + ";"),
      newline,
      "import com.google.gson.annotations.JsonAdapter;",
      "import org.eclipse.lsp4j.jsonrpc.json.adapters.EnumTypeAdapter;",
      newline,
      "@JsonAdapter(EnumTypeAdapter.Factory.class)",
      block(s"public enum $tpe")(
        newline,
        values.map(renderEnumValueDef(enumType)),
        newline,
        s"private final $evt value;",
        block(s"$tpe($evt value)") {
          "this.value = value;"
        },
        newline,
        block(s"public $evt getValue())") {
          "return value;"
        },
        newline,
        block(s"public static $tpe forValue (${evt} value))")(
          s"$tpe[] allValues = $tpe.values();",
          "if (value < 1 || value > allValues.length)",
          lines("""throw new IllegalArgumentException("Illegal enum value: " + value);""").indent,
          "return allValues[value - 1],;"
        )
      )
    )
    val fileName = shapeId.getName() + ".java"
    CodegenFile(baseRelPath / fileName, allLines.render)
  }

  def enumValueType[A](enumType: EnumType[A]): String = enumType match {
    case IntEnum    => "int"
    case StringEnum => "String"
  }

  def renderEnumValueDef[A](enumType: EnumType[A]): EnumValue[A] => String = {
    enumType match {
      case IntEnum    => (ev: EnumValue[Int]) => s"${ev.name}(${ev.value})"
      case StringEnum => (ev: EnumValue[String]) => s"${ev.name}(\"${ev.value}\")"
    }
  }

  def renderPkg(shapeId: ShapeId): Lines = lines(
    s"package $basepkg"
  )

  def renderImports(fields: List[Field]): Lines =
    fields.map(_.tpe).foldMap(renderImport(_)).distinct.sorted

  def renderImport(tpe: Type): Lines = tpe match {
    case TRef(shapeId) => empty // assuming everything is generated in the same package
    case TMap(key, value) =>
      lines(s"import java.util.collection.Map") ++ renderImport(key) ++ renderImport(value)
    case TCollection(member) =>
      lines(s"import java.util.collection.List") ++ renderImport(member)
    case TUntaggedUnion(tpes) => tpes.foldMap(renderImport)
    case TPrimitive(prim)     => empty
  }

  def renderJavaField(field: Field): Lines = {
    val maybeAdapter = if (field.tpe == Type.TPrimitive(Primitive.PDocument)) {
      lines("@JsonAdapter(JsonElementTypeAdapter.Factory.class)")
    } else empty
    val maybeNonNull = if (field.required) lines("@NonNull") else empty
    lines(
      maybeAdapter,
      maybeNonNull,
      renderFieldRaw(field)
    )
  }

  def renderParam(field: Field): String = {
    val decl = renderFieldRaw(field)
    if (field.required) {
      s"@NonNull $decl"
    } else decl
  }

  def renderFieldRaw(field: Field): String = {
    s"${renderType(field.tpe)} ${field.name}"
  }

  def renderType(tpe: Type): String = tpe match {
    case TRef(shapeId)        => shapeId.getName()
    case TPrimitive(prim)     => renderPrimitive(prim)
    case TMap(key, value)     => s"Map<${renderType(key)}, ${renderType(value)}>"
    case TCollection(member)  => s"List<${renderType(member)}>"
    case TUntaggedUnion(tpes) => renderType(tpes.head) // Todo what does bsp4j do ?
  }

  def renderPrimitive(prim: Primitive) = prim match {
    case PFloat    => "Float"
    case PDouble   => "Double"
    case PUnit     => "void"
    case PString   => "String"
    case PInt      => "Integer"
    case PDocument => "Object"
    case PBool     => "Boolean"
    case PLong     => "Long"
  }

}