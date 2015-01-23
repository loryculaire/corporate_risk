package controllers

import play.api._
import play.api.mvc._
import deductions.runtime.html.{ CreationForm, TableView }
import deductions.runtime.services.FormSaver
import deductions.runtime.sparql_cache.RDFCache
import java.net.URLDecoder

import models.UserData
import Auth._

object Application extends Controller with Secured with RDFCache {
  lazy val tableView = new TableView {}

  def index = withUser { implicit user =>
    implicit request =>
      Ok(views.html.index(UserData.getUserData(user).map(_.getURI)))
  }

  /** edit given URI */
  def form(uri: String) = withUser { implicit user =>
    implicit request =>
      println("editURI: " + request)
      Ok(views.html.form(tableView.htmlForm(uri, editable = true
      //        lang = chooseLanguage(request)
      ).get))

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
          new FormSaver().saveTriples(form.data)
          form.data.getOrElse("uri", Seq()).headOption match {
            case Some(url) => URLDecoder.decode(url, "utf-8")
            case _ => throw new IllegalArgumentException
          }
        case _ => throw new IllegalArgumentException
      }

      Ok(views.html.report(new TableView {}.htmlForm(uri).get))
  }

}