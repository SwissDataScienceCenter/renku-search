<!-- -*- fill-column: 80 -*- -->
## Search Query

The search accepts queries as a query string. A query may contain
specific and unspecific search terms.

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

### Fields

The following fields are available:

- `id`
- `name`
- `slug`
- `visibility`
- `created`
- `createdBy`
- `type`
- `role`
- `keyword`
- `namespace`

Each field allows to specify one or more values, separated by comma.
The value must be separated by a `:`. For date fields, additional `<`
and `>` is supported.

### EntityTypes

The field `type` allows to search for specific entity types. If it is
missing, all entity types are included in the result. Entity types are:

- `Project`
- `User`
- `Group`

Example:
 `type:Project`

### Roles

The field `role` allows to search for projects the current user has
the given role. Other entities are excluded from the results.

- `owner`
- `editor`
- `viewer`
- `member`

### Visibility

The `visibility` field can be used to restrict to entities with a
certain visibility. Users have a default visibility of `public`.
Possbile values are:

- `public`
- `private`



### Dates

Date fields, like

- `created`

accept date strings which can be specified in various ways. There are

- relative dates: `today`
- partial timestamps: `2023-05`, `2023-11-12T10`
- calculations based on the above: `today-5d`, `2023-10-15/10d`


#### Relative dates

There are the following keywords for relative dates:

- `today`
- `yesterday`

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

## Sorting

The query allows to define terms for sorting. Sorting is limited to
specific fields, which are:

- `name`
- `created`
- `score`

Sorting by a field is defined by writing the field name, followed by a
dash and the sort direction. Multiple such definitions can be
specified, using a comma separated list. Alternatively, multiple
`sort:…` terms will be combined into a single one in the order they
appear.

Example:
`sort:score-desc,created-asc`
is equivalent to
`sort:score-desc sort:created-asc`
