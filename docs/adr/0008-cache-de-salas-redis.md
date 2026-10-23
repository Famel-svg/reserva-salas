# ADR 0008: Cache de salas no Redis

Status: Aceita

## Contexto

`GET /api/rooms` e `GET /api/rooms/{id}` são as leituras mais frequentes (toda reserva consulta salas) e os dados mudam raramente, só por ações de administrador. Redis e `spring-boot-starter-cache` já fazem parte da stack.

## Decisão

- Cache `rooms` aplicado no `RoomService` (não no controller) com `@Cacheable`: a lista usa a chave padrão e cada sala usa o id como chave.
- `create`, `update` e `delete` aplicam `@CacheEvict(allEntries = true)`. Descartar tudo é simples e correto, porque a lista depende de todas as salas e o volume de escritas é desprezível.
- O cache guarda `RoomResponse` (record), nunca a entidade JPA `Room`, que carrega estado de persistência e não serializa de forma estável. Por isso o service passou a devolver DTOs. As listas são `ArrayList`, pois o Jackson não relê a lista imutável de `toList()` com o tipo embutido.
- `CacheConfig` configura `RedisCacheManager` com `GenericJackson2JsonRedisSerializer` (JSON legível e sem `Serializable`), TTL de 10 minutos e sem cache de `null`.
- `CacheErrorHandler` registra um aviso e segue em qualquer falha de leitura, gravação ou invalidação do cache: Redis fora do ar vira cache miss e a consulta cai no banco, em vez de 500.

## Consequências

- Leituras repetidas não tocam o Postgres. Os ITs provam isso com `@MockitoSpyBean` no `RoomRepository` e `verify(times(1))`; Redis e Postgres são reais (Testcontainers).
- Consistência: com o Redis saudável, a escrita invalida o cache no retorno do método; entre a invalidação e o commit uma leitura concorrente pode repopular o valor antigo, janela curta e limitada pelo TTL. Se o Redis estiver fora no momento da invalidação, o erro é só logado e leituras posteriores podem devolver dado velho por até 10 minutos (consistência eventual até o TTL). Aceitamos o trade-off: disponibilidade da leitura vale mais que frescor perfeito de dados que mudam raramente.
- O cache muda apenas o custo da leitura, não o conteúdo das respostas nem as regras de negócio.
- Mudar o formato de `RoomResponse` pode deixar entradas incompatíveis no Redis; a leitura falha, é logada e vira miss, e o TTL limpa o resto.
- Com Redis fora, cada leitura espera o timeout da conexão antes de cair no banco; circuit breaker fica como evolução se isso importar.
