import type { Metadata, ResolvingMetadata } from 'next';
import { getApolloClient } from '@/lib/graphql/client';
import { extractIdFromSemanticPath } from '@/shared/utils/url';
import { 
  BacenInstitutionDocument, 
  type BacenInstitutionQuery, 
  type BacenInstitutionQueryVariables 
} from '@/features/financial-data/view-institution-detail/api/index.generated';
import { getDictionary, interpolate } from '@/shared/i18n/server';
import BacenInstitutionDetailClient from './client';

export const dynamic = 'force-dynamic';

type Props = {
  params: Promise<{ "bacen-cod-inst": string }>
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>
}

export async function generateMetadata(
  { params }: Props,
  parent: ResolvingMetadata
): Promise<Metadata> {
  const { "bacen-cod-inst": rawParam } = await params;
  const id = extractIdFromSemanticPath(rawParam);

  // Default to PT
  const t = getDictionary('pt').seo.institution;

  if (!id) {
    return {
      title: t.notFoundTitle,
    };
  }

  try {
    const client = getApolloClient();
    const { data } = await client.query<BacenInstitutionQuery, BacenInstitutionQueryVariables>({
      query: BacenInstitutionDocument,
      variables: { id },
    });

    if (!data) {
      return {
        title: t.notFoundTitle,
      };
    }

    const institution = data.bacenFinanceInstitution;

    if (!institution) {
      return {
        title: t.notFoundTitle,
      };
    }

    const title = interpolate(t.title, { name: institution.name });
    const description = interpolate(t.description, { name: institution.name });

    return {
      title,
      description,
      openGraph: {
        title,
        description,
        type: 'website',
      },
    };
  } catch (error) {
    console.error('Error generating metadata for institution:', error);
    return {
      title: 'Detalhes da Instituição',
    };
  }
}

export default function BacenInstitutionDetailPage() {
  return <BacenInstitutionDetailClient />;
}
