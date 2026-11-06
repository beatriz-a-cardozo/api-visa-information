package com.tcc.api_visa.service;

import com.tcc.api_visa.domain.Country;
import com.tcc.api_visa.domain.OfficialSource;
import com.tcc.api_visa.repository.CountryRepository;
import com.tcc.api_visa.security.PublicUrlValidator;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Maintains the official sources of each country. Every change is a single atomic update on the
 * country document, so two administrators editing at the same time cannot lose each other's change,
 * and a URL is never stored twice for the same country.
 */
@Service
@RequiredArgsConstructor
public class OfficialSourceService {

    private static final String SOURCES = "officialSources";
    private static final String SOURCE_URL = SOURCES + ".url";

    private final MongoTemplate mongoTemplate;
    private final CountryRepository countryRepository;
    private final PublicUrlValidator urlValidator;
    private final Clock clock;

    public List<OfficialSource> list(String iso3) {
        return List.copyOf(country(iso3).getOfficialSources());
    }

    public OfficialSource add(String iso3, SourceChange change) {
        OfficialSource source = toSource(change);
        Query query = Query.query(Criteria.where("_id").is(iso3).and(SOURCE_URL).ne(source.url()));
        if (mongoTemplate
                        .updateFirst(query, new Update().push(SOURCES, source), Country.class)
                        .getMatchedCount()
                == 0) {
            country(iso3); // 404 when the country does not exist
            throw new DuplicateSourceException(iso3, source.url());
        }
        return source;
    }

    /** Replaces the source currently registered under {@code url}; the URL itself may change. */
    public OfficialSource replace(String iso3, String url, SourceChange change) {
        OfficialSource source = toSource(change);
        Criteria criteria = Criteria.where("_id").is(iso3);
        criteria = source.url().equals(url)
                ? criteria.and(SOURCE_URL).is(url)
                : criteria.andOperator(
                        Criteria.where(SOURCE_URL).is(url),
                        Criteria.where(SOURCE_URL).ne(source.url()));
        if (mongoTemplate
                        .updateFirst(Query.query(criteria), new Update().set(SOURCES + ".$", source), Country.class)
                        .getMatchedCount()
                == 0) {
            throw notReplaced(iso3, url, source.url());
        }
        return source;
    }

    public void remove(String iso3, String url) {
        Query query = Query.query(Criteria.where("_id").is(iso3).and(SOURCE_URL).is(url));
        Update update = new Update().pull(SOURCES, new Document("url", url));
        if (mongoTemplate.updateFirst(query, update, Country.class).getMatchedCount() == 0) {
            country(iso3);
            throw new SourceNotFoundException(iso3, url);
        }
    }

    private RuntimeException notReplaced(String iso3, String url, String newUrl) {
        List<OfficialSource> sources = country(iso3).getOfficialSources();
        boolean exists = sources.stream().anyMatch(source -> source.url().equals(url));
        return exists ? new DuplicateSourceException(iso3, newUrl) : new SourceNotFoundException(iso3, url);
    }

    private OfficialSource toSource(SourceChange change) {
        String url = urlValidator.validate(change.url()).toString();
        return new OfficialSource(
                url, change.description().strip(), change.language(), change.verified() ? clock.instant() : null);
    }

    private Country country(String iso3) {
        return countryRepository.findById(iso3).orElseThrow(() -> new UnknownCountryException(iso3));
    }
}
