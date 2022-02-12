package dsp.schema.repo

import dsp.schema.core.services.SchemaRepo
import dsp.schema.core.domain.SchemaDomain.{UserID, UserProfile}
import zio.{Task, UIO, ZIO}

trait SchemaRepoLive extends SchemaRepo {
  def schemaRepo: SchemaRepo.Service =
    new SchemaRepo.Service {
      def lookup(id: UserID): Task[UserProfile] =
        zio.Task.succeed("Our great user profile")
      def update(id: UserID, profile: UserProfile): Task[Unit] = {
        println(s"updating $id, with $profile")
        zio.Task.succeed(())
      }

    }
}
object SchemaRepoLive extends SchemaRepoLive
