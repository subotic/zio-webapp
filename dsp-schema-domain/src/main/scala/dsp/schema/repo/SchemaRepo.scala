package dsp.schema.repo

import dsp.schema.domain.SchemaDomain.{UserID, UserProfile}
import zio.{Task, UIO, ZIO, RIO}

trait SchemaRepo {
  def lookup(id: UserID): Task[UserProfile]
  def update(id: UserID, profile: UserProfile): Task[Unit]
}

object SchemaRepo {
  def lookup(id: UserID): RIO[SchemaRepo, UserProfile] =
    ZIO.serviceWithZIO(_.lookup(id))

  def update(id: UserID, profile: UserProfile): RIO[SchemaRepo, Unit] =
    ZIO.serviceWithZIO(_.update(id, profile))
}
