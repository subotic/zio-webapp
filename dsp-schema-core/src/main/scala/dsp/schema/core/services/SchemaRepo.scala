package dsp.schema.core.services

import dsp.schema.core.domain.SchemaDomain.{UserID, UserProfile}
import zio.{Task, UIO, ZIO, RIO}

object SchemaRepo {
  trait Service {
    def lookup(id: UserID): Task[UserProfile]
    def update(id: UserID, profile: UserProfile): Task[Unit]
  }
}

trait SchemaRepo {
  def schemaRepo: SchemaRepo.Service
}

object repo {
  def lookup(id: UserID): RIO[SchemaRepo, UserProfile] =
    ZIO.serviceWithZIO(_.schemaRepo.lookup(id))

  def update(id: UserID, profile: UserProfile): RIO[SchemaRepo, Unit] =
    ZIO.serviceWithZIO(_.schemaRepo.update(id, profile))
}