import { ref, type Ref } from "vue";

interface UseRequestOptions<T> {
  immediate?: boolean;
  initialData?: T;
  onSuccess?: (data: T) => void;
  onError?: (error: unknown) => void;
}

export function useRequest<T = unknown>(
  apiFn: (...args: unknown[]) => Promise<T>,
  options: UseRequestOptions<T> = {}
) {
  const loading = ref(false);
  const data = ref(options.initialData || null) as Ref<T | null>;
  const error = ref<unknown>(null);

  const run = async (...args: unknown[]) => {
    loading.value = true;
    error.value = null;
    try {
      const res = await apiFn(...args);
      data.value = res;
      if (options.onSuccess) {
        options.onSuccess(res);
      }
      return res;
    } catch (err) {
      error.value = err;
      if (options.onError) {
        options.onError(err);
      }
      throw err;
    } finally {
      loading.value = false;
    }
  };

  if (options.immediate) {
    run();
  }

  return { loading, data, error, run };
}
