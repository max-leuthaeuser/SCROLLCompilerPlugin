package scroll.internal

import scala.collection.JavaConversions._
import com.typesafe.config.ConfigFactory
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.impl.DynamicEObjectImpl
import org.eclipse.emf.ecore.{EObject, EPackage}
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.util.{BasicExtendedMetaData, EcoreEList}
import org.eclipse.emf.ecore.xmi.XMLResource
import org.eclipse.emf.ecore.xmi.impl.{EcoreResourceFactoryImpl, XMIResourceFactoryImpl}
import scroll.internal.formal.FormalCROM

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class SCROLLCompilerPluginConfig() {
  private val NATURALTYPE = "NaturalType"
  private val ROLETYPE = "RoleType"
  private val COMPARTMENTTYPE = "CompartmentType"
  private val ROLEGROUP = "RoleGroup"
  private val RELATIONSHIP = "Relationship"
  private val FULFILLMENT = "Fulfillment"
  private val PART = "Part"
  private val validTypes = Set(NATURALTYPE, ROLEGROUP, ROLETYPE, COMPARTMENTTYPE, RELATIONSHIP, FULFILLMENT, PART)
  protected var crom = Option.empty[FormalCROM[String, String, String, String]]

  private val config = ConfigFactory.load()
  val compileTimeErrors: Boolean = config.getBoolean("compile-time-errors")
  val modelFile: String = config.getString("model-file")

  def getPlays = this.crom.get.fills

  def settings: String = s"\tcompile-time-errors: $compileTimeErrors\n\tmodel-file: $modelFile"

  withModel(modelFile)

  private def registerMetaModel(rs: ResourceSetImpl): Unit = {
    Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap.put(
      "ecore", new EcoreResourceFactoryImpl())

    val extendedMetaData = new BasicExtendedMetaData(rs.getPackageRegistry)
    rs.getLoadOptions.put(XMLResource.OPTION_EXTENDED_META_DATA, extendedMetaData)

    val r = rs.getResource(URI.createURI("archive:" + getClass.getClassLoader.getResource("crom_l1_composed.ecore").getPath, true), true)
    val eObject = r.getContents.get(0)
    eObject match {
      case p: EPackage => val _ = rs.getPackageRegistry.put(p.getNsURI, p)
      case _ => throw new IllegalStateException("Meta-Model for CROM could not be loaded!")
    }
  }

  /**
    * Load and imports an ecore model.
    * Remember to set the <code>path</code> variable!
    *
    * @return the imported model as Resource
    */
  protected def loadModel(): Resource = {
    val resourceSet = new ResourceSetImpl()
    registerMetaModel(resourceSet)
    resourceSet.getResourceFactoryRegistry.getExtensionToFactoryMap.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl())
    val r = resourceSet.getResource(URI.createURI(modelFile), true)

    require(null != r)
    require(!r.getContents.isEmpty)
    r
  }

  /**
    * Load and replace the current model instance.
    *
    * @param path the file path to load a CROM from
    */
  def withModel(path: String): Unit = {
    require(null != path && path.nonEmpty)
    crom = Option(construct())
  }

  /**
    * Checks if the loaded CROM is wellformed.
    *
    * @return true if a model was loaded using `withModel()` and it is wellformed, false otherwise
    */
  def wellformed: Boolean = crom.isDefined && crom.forall(_.wellformed)

  private def getInstanceName(of: EObject): String = of.eClass().getEAllAttributes.find(_.getName == "name") match {
    case Some(a) => of.eGet(a).toString
    case None => "-"
  }

  private def constructNT[NT >: Null](elem: EObject): NT = getInstanceName(elem).asInstanceOf[NT]

  private def constructRT[RT >: Null](elem: EObject): RT = getInstanceName(elem).asInstanceOf[RT]

  private def constructCT[CT >: Null](elem: EObject): CT = getInstanceName(elem).asInstanceOf[CT]

  private def constructRST[RST >: Null](elem: EObject): RST = getInstanceName(elem).asInstanceOf[RST]

  private def constructFills[NT >: Null, RT >: Null](elem: EObject): List[(NT, RT)] = {
    val obj = elem.asInstanceOf[DynamicEObjectImpl]
    val filler = obj.dynamicGet(1).asInstanceOf[DynamicEObjectImpl].dynamicGet(0).asInstanceOf[NT]
    val filledObj = obj.dynamicGet(0).asInstanceOf[DynamicEObjectImpl]
    if (filledObj.eClass().getName == ROLEGROUP) {
      collectRoles(filledObj).map(r => (filler, getInstanceName(r).asInstanceOf[RT]))
    } else {
      val filled = obj.dynamicGet(0).asInstanceOf[DynamicEObjectImpl].dynamicGet(0).asInstanceOf[RT]
      List((filler, filled))
    }
  }

  private def collectRoles(of: EObject): List[EObject] = of.eContents().toList.flatMap(e => e.eClass().getName match {
    case ROLEGROUP => collectRoles(e)
    case ROLETYPE => List(e)
    case PART => collectRoles(e)
    case _ => List()
  })

  private def constructParts[CT >: Null, RT >: Null](elem: EObject): (CT, List[RT]) = {
    val ct = getInstanceName(elem.eContainer()).asInstanceOf[CT]
    val roles = collectRoles(elem).map(r => getInstanceName(r).asInstanceOf[RT])
    (ct, roles)
  }

  private def constructRel[RST >: Null, RT >: Null](elem: EObject): (RST, List[RT]) = {
    val rstName = getInstanceName(elem).asInstanceOf[RST]
    val roles = collectRoles(elem.eContainer())
    val rsts = roles.filter(role => {
      val incoming = role.asInstanceOf[DynamicEObjectImpl].dynamicGet(1).asInstanceOf[EcoreEList[DynamicEObjectImpl]]
      val inCond = incoming match {
        case null => false
        case _ => incoming.exists(e => e.dynamicGet(0).asInstanceOf[String] == rstName)
      }
      val outgoing = role.asInstanceOf[DynamicEObjectImpl].dynamicGet(2).asInstanceOf[EcoreEList[DynamicEObjectImpl]]
      val outCond = outgoing match {
        case null => false
        case _ => outgoing.exists(e => e.dynamicGet(0).asInstanceOf[String] == rstName)
      }
      inCond || outCond
    }).map(getInstanceName(_).asInstanceOf[RT])
    (rstName, rsts)
  }

  private def addToMap(m: mutable.Map[String, List[String]], elem: (String, List[String])): Unit = {
    val key = elem._1
    val value = elem._2
    if (m.contains(key)) {
      m(key) = m(key) ++ value
    } else {
      val _ = m += elem
    }
  }

  private def construct[NT >: Null, RT >: Null, CT >: Null, RST >: Null](): FormalCROM[NT, RT, CT, RST] = {
    val nt = ListBuffer[String]()
    val rt = ListBuffer[String]()
    val ct = ListBuffer[String]()
    val rst = ListBuffer[String]()
    val fills = ListBuffer[(String, String)]()
    val parts = mutable.Map[String, List[String]]()
    val rel = mutable.Map[String, List[String]]()

    loadModel().getAllContents.filter(e => validTypes.contains(e.eClass().getName)).foreach(curr => {
      curr.eClass().getName match {
        case NATURALTYPE => nt += constructNT(curr)
        case ROLETYPE => rt += constructRT(curr)
        case COMPARTMENTTYPE => ct += constructCT(curr)
        case RELATIONSHIP =>
          rst += constructRST[String](curr)
          addToMap(rel, constructRel[String, String](curr))
        case FULFILLMENT => fills ++= constructFills(curr)
        case PART => addToMap(parts, constructParts[String, String](curr))
        case _ =>
      }
    })
    FormalCROM(nt.result(), rt.result(), ct.result(), rst.result(), fills.result(), parts.toMap, rel.toMap).asInstanceOf[FormalCROM[NT, RT, CT, RST]]
  }

}
