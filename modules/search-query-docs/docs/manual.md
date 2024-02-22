## Search Query

**NOTE: this is a work in progress**

The search accepts queries in two representations: JSON and a simple
query string. A query may contain specific and unspecific search
terms.

### Query String

A query is a sequence of words. All words that are not recognized as
specific search terms are used for searching in various entity
properties, such as `name` or `description`. Specific search terms are
matched exactly against a certain field. Terms are separated by
whitespace.

Example:
```
numpy flight visibility:public
```

Searches for entities containing `numpy` _and_ `flight` that are
public.

The term order is usually not relevant, it may influence the score of
a result, though.

If a value for a specific field contains whitespace, quotes or a comma
it must be enclosed in quotes. Additionally, multiple values can be
provided for each field by using a comma separated list. The values
are treated as alternatives, so any such value would yield a result.

Example:
```
numpy flight visibility:public,private
```

Searches for entities containing `numpy` _and_ `flight` that are
_either_ `public` _or_ `private`.

### Query JSON

The JSON format allows to specify the same query as a JSON object. A
JSON object may contain specific terms by including the corresponding
field-value pair. For unspecific terms, the special field `_text` is
used.

Example:
```json
{
  "_text": "numpy flight",
  "visibility": "public"
}
```

JSON objects are sequences of key-value pairs. As such, the encoding
allows to specifiy multiple same named fields in one JSON object. This
would be a valid query:

```json
{
  "_text": "numpy",
  "visibility": "public",
  "_text": "flight"
}
```

The JSON variant follows the same rules for specifying field values.
Multiple alternative values can be given as a comma separated list.

### Fields

The following fields are available:

```scala mdoc:passthrough
import io.renku.search.query.*
println(Field.values.map(e => s"`${e.name}`").mkString("- ", "\n- ", ""))
```

Each field allows to specify one or more values, separated by comma.
The value must be separated by a `:`. For date fields, additional `<`
and `>` is supported.

### Dates

Date fields, like

```scala mdoc:passthrough
println(List(Field.Created).map(e => s"`${e.name}`").mkString("- ", "\n- ", ""))
```

accept date strings which can be specified in various ways. There are

- relative dates: `today`
- partial timestamps: `2023-05`, `2023-11-12T10`
- calculations based on the above: `today-5d`, `2023-10-15/10d`


#### Relative dates

There are the following keywords for relative dates:

```scala mdoc:passthrough
println(
  RelativeDate.values.map(e => s"`${e.name}`").mkString("- ", "\n- ", "")
)
```

#### Partial Timestamps

Timestamps must be in ISO8601 form and are UTC based and allow to
specify time up to seconds. The full form is

```
yyyy-mm-ddTHH:MM:ssZ
```

Any part starting from right can be omitted. When querying, it will be
filled with either the maximum or minimum possible value depending on
the side of comparison. When the date is an upper bound, the missing
parts will be set to their minimum values. Conversely, when used as a
lower bound then the parts are set to its maximum value.

Example:
- `created>2023-03` will turn into `created>2023-03-31T23:59:59`
- `created<2023-03` will turn into `created<2023-03-01T00:00:00`

#### Date calculations

At last, a date can be specified by adding or subtracting days from a
reference date. The reference date must be given either as a relative
date or partial timestamp. Then a `+`, `-` or `/` follows with the
amount of days.

The `/` character allows to add and substract the days from the
reference date, making the reference date the middle.

Example:
- `created>today-14d` things created from 14 days ago
- `created<2023-05/14d` things created from last two weeks of April
  and first two weeks of May

#### Date Comparison

Comparing dates with `>` and `<` is done as expected. More interesting
is to specify more than one date and the use of the `:` comparison.

The `:` can be used to specify ranges more succinctly. For a full
timestamp, it means /equals/. With partial timestamps it searches
within the minimum and maximum possible date for that partial
timestamp.

Since multiple values are combined using `OR`, it is possible to
search in multiple ranges.

Example:
```
created:2023-03,2023-06
```

The above means to match entities created in March 2023 or June 2023.
