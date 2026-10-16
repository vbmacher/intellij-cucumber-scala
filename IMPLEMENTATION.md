# Open-ticket implementation map

Scope: #179–218 and #224–229. #219–223 are deferred: AI/semantic search and cloud
integrations were excluded by request; #221 remains excluded explicitly. This file
maps implementation and automated evidence. GitHub issue state has not been changed.

| Tickets | Implementation / user surface | Focused evidence |
| --- | --- | --- |
| #179, #180 | Indexed custom parameter return types and localized semantic snippet keywords | `CustomParameterCreationSpec`, `LocalizedStepCreationSpec` |
| #181 | Explicit preview and deduplicated batch creation in a writable existing Scala type | `BatchStepCreationSpec`, destination fixtures |
| #182 | Native rename preview with capture-preserving usage changes and conservative conflict checks | `StepRenameSpec` |
| #183, #184 | Completion counts, provenance, tags, documentation and scenario preview | `StepCompletionSpec`, `StepDocumentationSpec` |
| #185 | Runtime-valid sample arguments in editable native templates | `StepTemplateSpec` |
| #186 | Quick Documentation captures, parameter types and trailing arguments | `StepDocumentationSpec` |
| #187, #188 | Distinct scenario usages, exact ambiguous candidates and source navigation | usage, reference-range and documentation fixtures |
| #189, #190 | Native Scala Cucumber configurations, feature/scenario/outline/row context producers | `RunSelectionSpec` |
| #191 | Identity-based native message tree, hooks/results/source navigation and location-based failed reruns | `RunResultsSpec`, recorded supported-runtime envelopes |
| #192 | Run-specific result gutters/docs, stale state, clear/disable controls | `RunResultsSpec` |
| #193, #194 | Runtime tag semantics, module-scoped previews, owning module/JDK/test runtime, glue and explicit overrides | `RunSelectionSpec`; CLI example execution |
| #195 | Dedicated feature/row debugger requests, thread-specific runtime context, session-owned highlights | `FeatureBreakpointsSpec`, real JDI runtime probe with one/two threads; interactive checks below |
| #196 | Searchable scoped catalog with semantic kind filter, preview/navigation and template insertion | suite, completion and template fixtures |
| #197 | Bounded existing-wording suggestions with verified capture-preserving preview | `EditorFeaturesSpec` |
| #198 | Indexed hook navigation and explicit unknown tag conditions | `RunSelectionSpec` |
| #199 | Optional follow-caret implementation viewer, candidate selection and pin | shares tested exact resolver; interactive checks below |
| #200, #201 | Marked feature-only wording, optional reuse/module/tag ranking and stable source tie-break | `StepCompletionSpec` |
| #202, #203 | Annotation-derived deprecation metadata and Scala custom-parameter completion | completion/docs fixtures |
| #204, #205 | Scoped unused report, identical expressions, observed ambiguity and duplicate ordered scenario bodies | `SuiteAnalysisSpec` |
| #206 | Known expression type checks and runtime-observed argument arity; unknown conversions stay unclassified | `StepSignatureSpec` |
| #207 | Examples header/placeholder consistency across blocks and structured arguments | `InspectionFeaturesSpec`, native outline fixtures |
| #208–211 | Optional cell navigation/clear, escaped TSV, movement, explicit structural deletion and previewed DataTable transpose | `GherkinTableSpec` |
| #212 | Editable Scala case-class source with conservative all-row inference and conflict checks | `SuiteAnalysisSpec` |
| #213 | Explicit entry-map aliases resolved to constructor fields; bounded header completion/diagnostics | `DataTableMappingsSpec` |
| #214, #215 | Module/tag/category scenario grouping, tag filter, native run selection and scoped health denominators | `SuiteAnalysisSpec`, `RunSelectionSpec` |
| #216, #217 | Native Source/Preview/Split, escaped local HTML, filtered HTML/PDF export with cancellation | `EditorFeaturesSpec`, optional `PdfExportSpec` |
| #218 | Explicit requirement mappings, precise local ID references and previewed URL navigation | `EditorFeaturesSpec`, `InspectionFeaturesSpec` |
| #224 | Optional workspace history, comparable contexts, attempts/interruptions, retention and clear/disable | `RunResultsSpec` |
| #225 | Explicit previewed numbering with stable title replacement | `EditorFeaturesSpec` |
| #226 | Native diagnostics/actions, outlines, localization, formatting/folding and typed DocString baseline | `NativeEditorBaselineSpec` and existing navigation/generation fixtures |
| #227 | Optional profile-based style checks and safe duplicate-tag fix | `InspectionFeaturesSpec` |
| #228 | Supported public Scala DSL inspected: no embedded-step execution API; ordinary Scala strings remain unchanged | pinned runtime sources and runnable CLI example; README conclusion |
| #229 | This implementation map and current usage documentation | README and example guide |

## Verification boundaries

Run all fixtures using JDK 25: `sbt --batch test`. Build the ZIP using
`sbt --batch 'cucumber-scala / packageArtifactZip'`.

The PDF fixture needs a JCEF-capable JetBrains Runtime. Run sbt with that JBR as
`JAVA_HOME`, then `sbt --batch 'cucumber-scala / testOnly *PdfExportSpec'`.
It is skipped under a runtime without JCEF. It renders a real PDF and verifies that
cancelling a replacement preserves the original bytes.

Run `JAVA_HOME=<JDK-25> python3 scripts/check-debugger-runtime.py` to exercise the actual
JDI context reader on the compiled example runtime with one and two concurrent threads.

Interactive sandbox checks remain separate from automated fixture evidence. The
[example guide](example/README.md#run-and-debugger-checks) covers imported module/run
configuration UI, breakpoint lifecycle and concurrent sessions, paired-view focus,
export layout/pagination and editor actions. A passing fixture suite alone does not
claim those interactions have been manually verified.
