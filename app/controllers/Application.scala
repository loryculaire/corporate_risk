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

import models.{ User, UserData, UserVocab, ResponseAnalysis }
import Auth._
import org.w3.banana.SparqlOpsModule
import org.w3.banana.RDFOpsModule
import deductions.runtime.abstract_syntax.InstanceLabelsInference2

object Application extends ApplicationTrait[Jena, Dataset]
  with RDFStoreLocalJena1Provider

trait ApplicationTrait[Rdf <: RDF, DATASET] extends Controller with Secured
    with UserDataTrait[Rdf, DATASET]
    with InstanceLabelsInference2 {

  lazy val tableView = new TableView {}
  import ops._

  def index = withUser { implicit user =>
    implicit request =>
      Ok(views.html.index(implicitly))
  }

  def saveinfo = withUser { implicit user =>
    implicit request => {
      Ok(views.html.index(implicitly))
    }
  }

  /**  */
  def formgroup(groupUri: String) = withUser { implicit user =>
    implicit request =>
      Ok(views.html.formgroup(getUserData(
        user, URI(groupUri)).map {
          case FormUserData(formUri, label) =>
            (fromUri(formUri), label,
              models.ResponseAnalysis.responsesCount(user, fromUri(formUri))
            )
        }))
  }

  /** edit given URI */
  def form(uri: String) = withUser { implicit user =>
    implicit request =>
      Ok(views.html.form(tableView.htmlFormElem(uri, editable = true, graphURI = user.getURI().toString())))
  }

  /** create new instance of given class (unused) */
  def create(url: String) = withUser { implicit user =>
    implicit request =>
      Ok(views.html.form(new CreationForm { actionURI = routes.Application.save.url }.create(url).get))
  }

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

  def report = withUser { implicit user =>
    implicit request =>
      Ok(views.html.report(ResponseAnalysis.report(user)))
  }

  def exportPDF = withUser { implicit user =>
    implicit request =>
      val renderer = new ITextRenderer
      val buffer = new ByteArrayOutputStream
      renderer.setDocumentFromString(views.html.pdfreport.render(user).toString)
      renderer.layout
      renderer.createPDF(buffer)
      Ok(buffer.toByteArray()).withHeaders(CONTENT_TYPE -> "application/pdf")
  }

  //TODO: handle security
  def chart(charttype: String) = Action {
    val content = charttype match {
      case "pie" => PieChart(Vector(("oui", 254), ("non", 167), ("NSPP", 88)))
      case "radar" => SpiderWebChart(Vector(("Sécurité", 4), ("Fiabilité", 1), ("Gouvernance", 4), ("Vitesse", 2), ("Solidité", 3)))
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
