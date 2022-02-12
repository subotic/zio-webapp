package dsp.schema.repo

import zio._

import dsp.schema.core.services.SchemaRepo

import dsp.schema.core.domain.SchemaDomain.{UserID, UserProfile}

class TestService extends SchemaRepo.Service {
  private var map: Map[UserID, UserProfile] = Map()

  def setTestData(map0: Map[UserID, UserProfile]): Task[Unit] =
    Task { map = map0 }

  def getTestData: Task[Map[UserID, UserProfile]] =
    Task(map)

  def lookup(id: UserID): Task[UserProfile] =
    Task(map(id))

  def update(id: UserID, profile: UserProfile): Task[Unit] =
    Task.attempt { map = map + (id -> profile) }
}

trait SchemaRepoTest extends SchemaRepo {
  val schemaRepo: TestService = new TestService
}

object SchemaRepoTest extends SchemaRepoTest
