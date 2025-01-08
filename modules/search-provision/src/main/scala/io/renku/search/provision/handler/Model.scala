package io.renku.search.provision.handler

import io.bullet.borer.Encoder
import io.bullet.borer.Writer
import io.renku.search.solr.documents.EntityDocument
import io.renku.search.solr.documents.PartialEntityDocument

type EntityOrPartial = EntityDocument | PartialEntityDocument

object EntityOrPartial:

  given (using
      ee: Encoder[EntityDocument],
      ep: Encoder[PartialEntityDocument]
  ): Encoder[EntityOrPartial] =
    new Encoder[EntityOrPartial] {
      def write(w: Writer, value: EntityOrPartial) =
        value match
          case e: EntityDocument        => ee.write(w, e)
          case p: PartialEntityDocument => ep.write(w, p)
    }

  given IdExtractor[EntityOrPartial] = _.id
