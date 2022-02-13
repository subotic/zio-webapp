package dsp.schema.repo

import dsp.schema.core.interfaces.SchemaRepo
import dsp.schema.core.domain.SchemaDomain.{UserID, UserProfile}
import zio.{Task, UIO, ZIO, URLayer}

case class SchemaRepoLive() extends SchemaRepo {

  override def lookup(id: UserID): Task[UserProfile] =
    zio.Task.succeed("Our great user profile")
  override def update(id: UserID, profile: UserProfile): Task[Unit] = {
    println(s"updating $id, with $profile")
    zio.Task.succeed(())
  }

}

object SchemaRepoLive extends (() => SchemaRepo) {
  val layer: URLayer[Any, SchemaRepo] = SchemaRepoLive().toLayer
}
