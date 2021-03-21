package service.store

import com.github.tminglei.slickpg._

package object postgres {

  trait ExtendedPostgresProfile extends ExPostgresProfile
    with PgArraySupport
    with PgDate2Support
    with PgRangeSupport
    with PgHStoreSupport
    with PgSearchSupport
    with PgNetSupport
    with PgLTreeSupport {

    override val api: ExtendedAPI.type = ExtendedAPI

    object ExtendedAPI extends API
      with ArrayImplicits
      with SimpleArrayPlainImplicits
      with DateTimeImplicits
      with Date2DateTimePlainImplicits
      with NetImplicits
      with LTreeImplicits
      with RangeImplicits
      with HStoreImplicits
      with SearchImplicits
      with SearchAssistants {}

  }

  object ExtendedPostgresProfile extends ExtendedPostgresProfile

}
