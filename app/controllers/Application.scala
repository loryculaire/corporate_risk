package controllers

import play.api._
import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import deductions.runtime.html.{ CreationForm, TableView }
import deductions.runtime.services.FormSaver
import deductions.runtime.jena.RDFStoreLocalJena1Provider
import deductions.runtime.sparql_cache.RDFCache
import java.net.URLDecoder
import java.io.ByteArrayOutputStream
import org.xhtmlrenderer.pdf.ITextRenderer
import scalax.chart.api._
import deductions.runtime.dataset.RDFStoreLocalProvider
import org.w3.banana.RDF
import org.w3.banana.jena.Jena
import com.hp.hpl.jena.query.Dataset
import models.FormUserData
import models.UserDataTrait
import models.{ User, UserCompanyInfo, UserData, UserVocab, ResponseAnalysis }
import Auth._
import org.w3.banana.SparqlOpsModule
import org.w3.banana.RDFOpsModule
import deductions.runtime.abstract_syntax.InstanceLabelsInference2
import models.UserCompanyInfo

object Application extends ApplicationTrait[Jena, Dataset]
  with RDFStoreLocalJena1Provider

trait ApplicationTrait[Rdf <: RDF, DATASET] extends Controller with Secured
    with UserDataTrait[Rdf, DATASET]
    with InstanceLabelsInference2[Rdf] {

  lazy val tableView = new TableView {}
  import ops._

  /**  */
  val userInfoForm = Form(
    mapping("department" -> optional(text),
      "naf" -> optional(text),
      "year" -> optional(text),
      "isGroup" -> optional(text)
    )(UserCompanyInfo.apply)(UserCompanyInfo.unapply)
  )

  /** Shows the index page with the info form */
  def index = withUser { implicit user =>
    implicit request =>
      val form = userInfoForm.bind(user.getInfo(user).getOrElse(new UserCompanyInfo).getMap)
      Ok(views.html.index(form))
  }

  /** Saves the info form for the user */
  def saveinfo = withUser { implicit user =>
    implicit request => {
      userInfoForm.bindFromRequest.fold(
        formWithErrors => BadRequest(views.html.index(formWithErrors)),
        userInfo => {
          user.saveInfo(user, userInfo)
          Ok(views.html.index(userInfoForm))
        }
      )
    }
  }

  /** show a list of forms */
  def formgroup(groupUri: String) = withUser { implicit user =>
    implicit request =>
      Ok(views.html.formgroup(getUserData(
        user, URI(groupUri)).map {
          case FormUserData(formUri, label) =>
            (fromUri(formUri), label,
              new ResponseAnalysis().responsesCount(user, fromUri(formUri))
            )
        }))
  }

  /** edit given form, with values previously set by the user */
  def form(uri: String) = withUser { implicit user =>
    implicit request =>
      Ok(views.html.form(tableView.htmlFormElem(uri, editable = true, graphURI = user.getURI().toString())))
  }

  /** saves the values entered by the user */
  def save = withUser { implicit user =>
    implicit request =>
      val uri = request.body match {
        case form: AnyContentAsFormUrlEncoded =>
          deductions.runtime.services.FormSaverObject.saveTriples(form.data)
          form.data.getOrElse("uri", Seq()).headOption match {
            case Some(url) => URLDecoder.decode(url, "utf-8")
            case _ => throw new IllegalArgumentException
          }
        case _ => throw new IllegalArgumentException
      }
      Redirect(routes.Application.index.url);
  }

  /** shows the report for the given user, as a html preview */
  def report = withUser { implicit user =>
    implicit request =>
      Ok(views.html.webreport(new ResponseAnalysis()))
  }

  /** downloads a pdf version of the report */
  def exportPDF = withUser { implicit user =>
    implicit request =>
      val renderer = new ITextRenderer
      val buffer = new ByteArrayOutputStream
      renderer.setDocumentFromString(views.html.pdfreport(new ResponseAnalysis()).toString)
      renderer.layout
      renderer.createPDF(buffer)
      Ok(buffer.toByteArray()).withHeaders(CONTENT_TYPE -> "application/pdf")
  }

  //TODO: handle security
  def chart(charttype: String, email: String) = Action {
    val user = User.find(email)
    val content = charttype match {
      case "risk" => SpiderWebChart(new ResponseAnalysis().getRiskEval.toVector)
      case "capital" => BarChart(new ResponseAnalysis().getRiskEval.toVector)
      case "pie" => PieChart(Vector(("Sécurité", 4), ("Fiabilité", 1), ("Gouvernance", 4), ("Vitesse", 2), ("Solidité", 3)))
    }
    Ok(content.encodeAsPNG(320, 320)).withHeaders(CONTENT_TYPE -> "image/png")
  }

  def contact() = Action {
    Ok(views.html.contact())
  }

  def info() = Action {
    Ok(views.html.info())
  }
}
